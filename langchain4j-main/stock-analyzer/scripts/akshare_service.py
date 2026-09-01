# -*- coding: utf-8 -*-
import argparse
import concurrent.futures
import datetime as dt
import hashlib
from html import unescape
import json
import math
from pathlib import Path
import re
import socket
import threading
import time
from io import StringIO
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, unquote, urlparse
from zoneinfo import ZoneInfo

import akshare as ak
import pandas as pd
import requests


REQUEST_TIMEOUT = 7
ORIGINAL_REQUEST = requests.sessions.Session.request
SECTOR_CACHE = {}
SECTOR_CACHE_LOCK = threading.Lock()
SECTOR_MEMBER_CACHE = {}
SECTOR_LIST_LAST_GOOD = None
STOCK_SECTOR_CACHE = {}
STOCK_BOARD_INDEX = {}
STOCK_PRIMARY_BOARD = {}
LEADER_FUNDAMENTALS_CACHE = {}
LIMIT_UP_CACHE = {}
THS_BOARD_CACHE = None
THS_BOARD_INDEX = {}
LEGACY_BOARD_INDEX = {}
THS_MEMBER_CACHE = {}
THS_LEADER_CACHE = {}
THS_STOCK_FIELD_CACHE = {}
LOCAL_SNAPSHOT_LOADED = False
LOCAL_SNAPSHOT_PATH = Path(__file__).resolve().parent.parent / "data" / "ths-board-snapshot.json"
SECTOR_DETAIL_LAST_GOOD = {}
SHANGHAI_ZONE = ZoneInfo("Asia/Shanghai")
AKSHARE_MINUTE_LOCK = threading.Lock()
SINA_REALTIME_FLOW_URL = (
    "http://money.finance.sina.com.cn/quotes_service/api/json_v2.php/"
    "MoneyFlow.ssl_bkzj_ssggzj"
)
EASTMONEY_REALTIME_FLOW_URL = "https://push2.eastmoney.com/api/qt/ulist.np/get"
EASTMONEY_UT = "b2884a393a59ad64002292a3e90d46a5"


def request_with_timeout(self, method, url, **kwargs):
    kwargs.setdefault("timeout", REQUEST_TIMEOUT)
    return ORIGINAL_REQUEST(self, method, url, **kwargs)


requests.sessions.Session.request = request_with_timeout


def clean(value):
    if value is None:
        return None
    try:
        if pd.isna(value):
            return None
    except (TypeError, ValueError):
        pass
    if hasattr(value, "item"):
        value = value.item()
    if isinstance(value, (dt.date, dt.datetime, pd.Timestamp)):
        return value.strftime("%Y-%m-%d")
    if isinstance(value, float) and (math.isnan(value) or math.isinf(value)):
        return None
    return value


def number(value):
    value = clean(value)
    if value is None or value == "":
        return None
    try:
        return float(str(value).replace(",", "").replace("%", ""))
    except (TypeError, ValueError):
        return None


def text(value):
    value = clean(value)
    return None if value is None else str(value).strip()


def row_value(row, *names):
    for name in names:
        if name in row.index:
            value = clean(row[name])
            if value is not None and value != "":
                return value
    return None


def index_board_members(board_id, frame):
    if frame is None or frame.empty:
        return
    for _, row in frame.iterrows():
        full_code = (text(row_value(row, "symbol")) or "").lower()
        if not re.fullmatch(r"(?:sh|sz|bj)\d{6}", full_code):
            raw_code = text(row_value(row, "code"))
            full_code = ths_stock_symbol(raw_code) or ""
        if not full_code:
            continue
        board_ids = STOCK_BOARD_INDEX.setdefault(full_code, [])
        if board_id not in board_ids:
            board_ids.append(board_id)


def local_board_summary(board_id):
    metadata = THS_BOARD_INDEX.get(board_id)
    if not metadata:
        return None
    member_cache = THS_MEMBER_CACHE.get(board_id)
    return {
        "id": board_id,
        "name": metadata.get("name"),
        "type": metadata.get("type", "概念"),
        "companyCount": len(member_cache[1]) if member_cache else 0,
        "changePercent": 0,
        "totalVolume": None,
        "totalAmount": 0,
        "netInflow": None,
        "netInflowRatio": None,
        "leaderCode": None,
        "leaderName": None,
        "leaderPrice": None,
        "leaderChangePercent": 0,
        "score": 50,
        "status": "观察",
        "classificationSource": metadata.get("source", "同花顺本地快照"),
    }


def load_local_board_snapshot():
    """Load the low-frequency board/constituent mapping once per sidecar run."""
    global LOCAL_SNAPSHOT_LOADED
    if LOCAL_SNAPSHOT_LOADED:
        return
    LOCAL_SNAPSHOT_LOADED = True
    try:
        if not LOCAL_SNAPSHOT_PATH.is_file():
            return
        payload = json.loads(LOCAL_SNAPSHOT_PATH.read_text(encoding="utf-8"))
        boards = payload.get("boards", {}) if isinstance(payload, dict) else {}
        for board_id, item in boards.items():
            if not isinstance(item, dict) or not item.get("name"):
                continue
            board_name = item.get("name")
            if str(board_id).startswith("ths_industry_local_"):
                board_name = re.sub(r"[ⅠⅡⅢⅣⅤⅥⅦⅧⅨⅩ]+$", "", board_name).strip() or board_name
            metadata = {
                "name": board_name,
                "code": item.get("code"),
                "type": item.get("type", "概念"),
                "source": item.get("source", "同花顺本地快照"),
                "legacyId": item.get("legacyId"),
            }
            THS_BOARD_INDEX[board_id] = metadata
            if item.get("legacyId"):
                LEGACY_BOARD_INDEX[item["legacyId"]] = board_id
            members = item.get("members")
            if isinstance(members, list) and members:
                frame = pd.DataFrame(members)
                if not frame.empty:
                    THS_MEMBER_CACHE[board_id] = (time.time(), frame)
                    index_board_members(board_id, frame)
            leaders = item.get("leaders")
            if isinstance(leaders, list) and leaders:
                frame = pd.DataFrame(leaders)
                if not frame.empty:
                    THS_LEADER_CACHE[board_id] = frame
        primary_boards = payload.get("primaryBoards", {}) if payload.get("version", 0) >= 3 else {}
        if isinstance(primary_boards, dict):
            for full_code, board_id in primary_boards.items():
                normalized_code = str(full_code).lower()
                if (re.fullmatch(r"(?:sh|sz|bj)\d{6}", normalized_code)
                        and board_id in THS_BOARD_INDEX):
                    STOCK_PRIMARY_BOARD[normalized_code] = board_id
    except Exception:
        # A corrupt snapshot must never prevent the sidecar from starting.
        return


def persist_local_board_snapshot():
    """Persist board metadata and normalized constituents atomically."""
    try:
        def records(frame):
            normalized = frame.astype(object).where(pd.notna(frame), None)
            return normalized.to_dict(orient="records")

        LOCAL_SNAPSHOT_PATH.parent.mkdir(parents=True, exist_ok=True)
        boards = {}
        for board_id, metadata in THS_BOARD_INDEX.items():
            item = {
                "name": metadata.get("name"),
                "code": metadata.get("code"),
                "type": metadata.get("type", "概念"),
                "source": metadata.get("source", "同花顺"),
                "legacyId": metadata.get("legacyId"),
            }
            cached = THS_MEMBER_CACHE.get(board_id)
            if cached and cached[1] is not None and not cached[1].empty:
                item["members"] = records(cached[1])
            leaders = THS_LEADER_CACHE.get(board_id)
            if leaders is not None and not leaders.empty:
                item["leaders"] = records(leaders)
            boards[board_id] = item
        payload = {
            "version": 3,
            "updatedAt": dt.datetime.now().isoformat(timespec="seconds"),
            "boards": boards,
            "primaryBoards": STOCK_PRIMARY_BOARD,
        }
        temporary = LOCAL_SNAPSHOT_PATH.with_suffix(".tmp")
        temporary.write_text(json.dumps(payload, ensure_ascii=False, allow_nan=False, default=clean), encoding="utf-8")
        temporary.replace(LOCAL_SNAPSHOT_PATH)
        with SECTOR_CACHE_LOCK:
            SECTOR_CACHE.pop("board-catalog", None)
            SECTOR_CACHE.pop("sector-list", None)
    except Exception:
        # Persistence is an optimization; never fail a live market request.
        return


def source(name, available, message=None):
    return {
        "name": name,
        "available": available,
        "message": message,
        "updatedAt": dt.datetime.now().isoformat(timespec="seconds"),
    }


def load_profile(code):
    result = {"profile": {}, "concepts": [], "sources": [], "warnings": []}
    try:
        business = ak.stock_zyjs_ths(symbol=code)
        if not business.empty:
            row = business.iloc[0]
            result["profile"].update({
                "mainBusiness": text(row_value(row, "主营业务")),
                "productTypes": text(row_value(row, "产品类型")),
                "productNames": text(row_value(row, "产品名称")),
                "businessScope": text(row_value(row, "经营范围")),
            })
            tags = []
            for field in ("产品类型", "产品名称"):
                raw = text(row_value(row, field))
                if raw:
                    tags.extend(re.split(r"[、,，;；/]+", raw))
            result["concepts"] = list(dict.fromkeys(tag.strip() for tag in tags if tag.strip()))[:10]
        result["sources"].append(source("同花顺主营资料", not business.empty))
    except Exception as exc:
        result["sources"].append(source("同花顺主营资料", False, str(exc)))
        result["warnings"].append("同花顺主营资料暂不可用")

    if not result["profile"].get("companyName") or not result["profile"].get("industry"):
        try:
            profile = ak.stock_profile_cninfo(symbol=code)
            if not profile.empty:
                row = profile.iloc[0]
                fallbacks = {
                    "companyName": text(row_value(row, "公司名称")),
                    "industry": text(row_value(row, "所属行业")),
                    "listDate": text(row_value(row, "上市日期")),
                    "mainBusiness": text(row_value(row, "主营业务")),
                    "businessScope": text(row_value(row, "经营范围")),
                }
                for key, value in fallbacks.items():
                    if not result["profile"].get(key) and value:
                        result["profile"][key] = value
                result["profile"]["introduction"] = text(row_value(row, "机构简介"))
            result["sources"].append(source("巨潮资讯公司资料", not profile.empty))
        except Exception as exc:
            result["sources"].append(source("巨潮资讯公司资料", False, str(exc)))
            result["warnings"].append("巨潮资讯公司资料暂不可用")
    return result


def metric_from_group(group, aliases):
    for alias in aliases:
        exact = group[group["metric_name"].astype(str) == alias]
        if not exact.empty:
            row = exact.iloc[0]
            return number(row_value(row, "value")), number(row_value(row, "yoy"))
    for _, row in group.iterrows():
        metric_name = text(row_value(row, "metric_name")) or ""
        if any(alias in metric_name for alias in aliases):
            return number(row_value(row, "value")), number(row_value(row, "yoy"))
    return None, None


def normalize_ths_performance(frame):
    periods = []
    if frame.empty or "report_date" not in frame.columns:
        return periods
    for report_date, group in frame.groupby("report_date", sort=False):
        revenue, revenue_yoy_ratio = metric_from_group(
            group, ["operating_income_total", "营业总收入", "营业收入"])
        revenue_yoy, _ = metric_from_group(
            group, ["calculate_operating_income_total_yoy_growth_ratio"])
        if revenue_yoy is None and revenue_yoy_ratio is not None:
            revenue_yoy = revenue_yoy_ratio * 100

        net_profit, net_profit_yoy_ratio = metric_from_group(
            group, ["parent_holder_net_profit", "归母净利润", "归属净利润"])
        net_profit_yoy, _ = metric_from_group(
            group, ["calculate_parent_holder_net_profit_yoy_growth_ratio"])
        if net_profit_yoy is None and net_profit_yoy_ratio is not None:
            net_profit_yoy = net_profit_yoy_ratio * 100

        adjusted, adjusted_yoy_ratio = metric_from_group(
            group, ["index_deduct_holder_net_profit", "扣非净利润", "扣除非经常性损益后的净利润"])
        adjusted_yoy, _ = metric_from_group(group, ["deduct_net_profit_yoy_growth_ratio"])
        if adjusted_yoy is None and adjusted_yoy_ratio is not None:
            adjusted_yoy = adjusted_yoy_ratio * 100

        eps, _ = metric_from_group(group, ["basic_eps", "基本每股收益"])
        roe, _ = metric_from_group(group, ["index_weighted_avg_roe", "净资产收益率"])
        gross_margin, _ = metric_from_group(group, ["sale_gross_margin", "毛利率"])
        debt_ratio, _ = metric_from_group(group, ["assets_debt_ratio", "资产负债率"])
        operating_cash, _ = metric_from_group(
            group, ["index_per_operating_cash_flow_net", "经营活动产生的现金流量净额", "经营现金流"])
        first = group.iloc[0]
        periods.append({
            "reportDate": text(report_date),
            "reportName": text(row_value(first, "report_name", "report_period")),
            "revenue": revenue,
            "revenueYoY": revenue_yoy,
            "netProfit": net_profit,
            "netProfitYoY": net_profit_yoy,
            "adjustedNetProfit": adjusted,
            "adjustedNetProfitYoY": adjusted_yoy,
            "eps": eps,
            "roe": roe,
            "grossMargin": gross_margin,
            "debtRatio": debt_ratio,
            "operatingCashFlow": operating_cash,
            "source": "同花顺",
        })
    return periods[:12]


def load_performance(code, full_code):
    warnings = []
    sources = []
    try:
        frame = ak.stock_financial_abstract_new_ths(symbol=code, indicator="按报告期")
        periods = normalize_ths_performance(frame)
        if periods:
            sources.append(source("同花顺财务指标", True))
            return {"performance": periods, "sources": sources, "warnings": warnings}
        sources.append(source("同花顺财务指标", False, "暂无数据"))
    except Exception as exc:
        sources.append(source("同花顺财务指标", False, str(exc)))
        warnings.append("同花顺财务指标暂不可用")
    return {"performance": [], "sources": sources, "warnings": warnings}


def load_tencent_price(full_code):
    response = requests.get(
        "http://qt.gtimg.cn/q=" + full_code,
        headers={"Referer": "https://finance.qq.com/"},
    )
    response.raise_for_status()
    response.encoding = "gbk"
    match = re.search(r'="([^"]*)"', response.text)
    if not match:
        return None
    fields = match.group(1).split("~")
    return number(fields[3]) if len(fields) > 3 else None


def latest_metric(frame, metric_name, annual=False):
    selected = frame[frame["metric_name"].astype(str) == metric_name]
    if annual:
        selected = selected[selected["report_date"].astype(str).str.endswith("12-31")]
    if selected.empty:
        return None
    return number(row_value(selected.iloc[0], "value"))


def load_valuation(code, full_code):
    try:
        frame = ak.stock_financial_abstract_new_ths(symbol=code, indicator="按报告期")
        if frame.empty:
            return {"valuation": None, "sources": [source("同花顺估值基础", False, "暂无数据")], "warnings": ["暂无估值数据"]}
        price = load_tencent_price(full_code)
        annual_eps = latest_metric(frame, "basic_eps", annual=True)
        net_assets_per_share = latest_metric(frame, "calc_per_net_assets")
        operating_cash_per_share = latest_metric(frame, "index_per_operating_cash_flow_net")
        valuation = {
            "date": text(frame.iloc[0]["report_date"]),
            "closePrice": price,
            "totalMarketValue": None,
            "peTtm": None,
            "peStatic": price / annual_eps if price and annual_eps and annual_eps > 0 else None,
            "pb": price / net_assets_per_share if price and net_assets_per_share and net_assets_per_share > 0 else None,
            "peg": None,
            "ps": None,
            "pcf": price / operating_cash_per_share if price and operating_cash_per_share and operating_cash_per_share > 0 else None,
            "industryPeTtm": None,
            "industryPb": None,
            "peRank": None,
            "peers": [],
        }
        return {"valuation": valuation, "sources": [source("同花顺财务指标 / 腾讯实时价格", True)], "warnings": []}
    except Exception as exc:
        return {"valuation": None, "sources": [source("同花顺估值基础", False, str(exc))], "warnings": ["估值数据暂不可用"]}


def load_reports(code):
    try:
        frame = ak.stock_research_report_em(symbol=code)
        reports = []
        for _, row in frame.head(10).iterrows():
            eps_forecasts = {}
            pe_forecasts = {}
            for column in frame.columns:
                match = re.fullmatch(r"(\d{4})-盈利预测-(收益|市盈率)", str(column))
                if not match:
                    continue
                forecast = number(row_value(row, column))
                if forecast is None:
                    continue
                target = eps_forecasts if match.group(2) == "收益" else pe_forecasts
                target[match.group(1)] = forecast
            reports.append({
                "title": text(row_value(row, "报告名称")),
                "rating": text(row_value(row, "东财评级")),
                "institution": text(row_value(row, "机构")),
                "date": text(row_value(row, "日期")),
                "industry": text(row_value(row, "行业")),
                "pdfUrl": text(row_value(row, "报告PDF链接")),
                "epsForecasts": eps_forecasts,
                "peForecasts": pe_forecasts,
            })
        return {
            "researchReports": reports,
            "sources": [source("东方财富个股研报（AKShare 原接口）", bool(reports))],
            "warnings": [] if reports else ["近期个股研报暂不可用"],
        }
    except Exception as exc:
        return {
            "researchReports": [],
            "sources": [source("东方财富个股研报（AKShare 原接口）", False, str(exc))],
            "warnings": ["个股研报暂不可用"],
        }


def load_industry_position(full_code, code):
    return {
        "industryPosition": None,
        "industryValuation": None,
        "peers": [],
        "sources": [],
        "warnings": ["同花顺当前未提供可稳定校验的同行名次，行业题材展示主营资料中的确认信息"],
    }


def build_snapshot(full_code):
    code = full_code[2:]
    jobs = {
        "profile": lambda: load_profile(code),
        "performance": lambda: load_performance(code, full_code),
        "valuation": lambda: load_valuation(code, full_code),
        "reports": lambda: load_reports(code),
        "industry": lambda: load_industry_position(full_code, code),
    }
    results = {}
    executor = concurrent.futures.ThreadPoolExecutor(max_workers=len(jobs))
    futures = {name: executor.submit(job) for name, job in jobs.items()}
    done, pending = concurrent.futures.wait(futures.values(), timeout=REQUEST_TIMEOUT * 3)
    for name, future in futures.items():
        if future in done:
            try:
                results[name] = future.result()
            except Exception as exc:
                results[name] = {"warnings": [f"{name} 数据处理失败: {exc}"], "sources": []}
        else:
            future.cancel()
            results[name] = {"warnings": [f"{name} 数据请求超时"], "sources": []}
    executor.shutdown(wait=False, cancel_futures=True)

    profile_result = results.get("profile", {})
    valuation_result = results.get("valuation", {})
    industry_result = results.get("industry", {})
    valuation = valuation_result.get("valuation")
    if valuation and industry_result.get("industryValuation"):
        valuation.update(industry_result["industryValuation"])
        valuation["peers"] = industry_result.get("peers", [])

    profile = profile_result.get("profile") or None
    industry_position = industry_result.get("industryPosition")
    if industry_position and profile:
        industry_position["industry"] = profile.get("industry")

    sources = []
    warnings = []
    for result in results.values():
        sources.extend(result.get("sources", []))
        warnings.extend(result.get("warnings", []))
    performance = results.get("performance", {}).get("performance", [])
    reports = results.get("reports", {}).get("researchReports", [])
    available = bool(profile or performance or valuation or reports or industry_position)
    return {
        "fullCode": full_code,
        "available": available,
        "profile": profile,
        "performance": performance,
        "valuation": valuation,
        "researchReports": reports,
        "industryPosition": industry_position,
        "concepts": profile_result.get("concepts", []),
        "sources": sources,
        "warnings": list(dict.fromkeys(warnings)),
        "fetchedAt": dt.datetime.now().isoformat(timespec="seconds"),
    }


def clamp(value, low=0.0, high=100.0):
    return max(low, min(high, value))


def cache_get(key, ttl_seconds):
    with SECTOR_CACHE_LOCK:
        item = SECTOR_CACHE.get(key)
        if not item or time.time() - item[0] > ttl_seconds:
            return None
        return item[1]


def cache_put(key, value):
    with SECTOR_CACHE_LOCK:
        SECTOR_CACHE[key] = (time.time(), value)
    return value


def amount_number(value):
    value = clean(value)
    if value is None or value == "":
        return None
    raw = str(value).replace(",", "").strip()
    multiplier = 1.0
    if "亿" in raw:
        multiplier = 100000000.0
    elif "万" in raw:
        multiplier = 10000.0
    match = re.search(r"[-+]?\d+(?:\.\d+)?", raw)
    return float(match.group()) * multiplier if match else None


def normalize_sector_name(value):
    value = text(value) or ""
    return re.sub(r"概念|行业|板块|制造业|制造|股份|\s+", "", value).strip().lower()


def board_summary(row, board_type, net_inflow=None, net_ratio=None, activity=50.0):
    change = number(row_value(row, "涨跌幅")) or 0
    leader_change = number(row_value(row, "个股-涨跌幅")) or 0
    score = 50 + change * 8 + leader_change * 1.5 + (activity - 50) * 0.18
    if net_ratio is not None:
        score += clamp(net_ratio * 2.5, -20, 20)
    score = round(clamp(score), 1)
    if score >= 75 and change > 0 and (net_inflow is None or net_inflow > 0):
        status = "主线候选"
    elif score >= 60:
        status = "活跃"
    elif score <= 35:
        status = "偏弱"
    else:
        status = "观察"
    return {
        "id": text(row_value(row, "label")),
        "name": text(row_value(row, "板块")),
        "type": board_type,
        "companyCount": int(number(row_value(row, "公司家数")) or 0),
        "changePercent": change,
        "totalVolume": number(row_value(row, "总成交量")),
        "totalAmount": number(row_value(row, "总成交额")) or 0,
        "netInflow": net_inflow,
        "netInflowRatio": net_ratio,
        "leaderCode": text(row_value(row, "股票代码")),
        "leaderName": text(row_value(row, "股票名称")),
        "leaderPrice": number(row_value(row, "个股-当前价")),
        "leaderChangePercent": leader_change,
        "score": score,
        "status": status,
    }


def load_ths_board_maps(refresh=False):
    """Load Tonghuashun board names/codes, while keeping the legacy catalog as fallback."""
    global THS_BOARD_CACHE
    load_local_board_snapshot()
    now = time.time()
    local_maps = {"行业": {}, "概念": {}}
    for board_id, metadata in THS_BOARD_INDEX.items():
        if str(board_id).startswith("ths_industry_local_"):
            continue
        board_type = metadata.get("type", "概念")
        name = text(metadata.get("name"))
        code = text(metadata.get("code"))
        if board_type in local_maps and name and code:
            local_maps[board_type][normalize_sector_name(name)] = metadata
    if any(local_maps.values()):
        THS_BOARD_CACHE = (now, local_maps, ["使用本地板块与成分股快照"])
        return local_maps, THS_BOARD_CACHE[2]
    if THS_BOARD_CACHE and not refresh:
        cached_maps = THS_BOARD_CACHE[1]
        cached_ttl = 300 if any(cached_maps.values()) else 15
        if now - THS_BOARD_CACHE[0] <= cached_ttl:
            return cached_maps, THS_BOARD_CACHE[2]

    maps = {"行业": {}, "概念": {}}
    warnings = []
    loaders = (("行业", getattr(ak, "stock_board_industry_name_ths", None), "同花顺"),
               ("概念", getattr(ak, "stock_board_concept_name_ths", None), "同花顺"))
    fallbacks = {"行业": getattr(ak, "stock_board_industry_name_em", None),
                 "概念": getattr(ak, "stock_board_concept_name_em", None)}
    for board_type, loader, classification_source in loaders:
        last_error = None
        try:
            direct = load_ths_board_names_direct(board_type)
            if direct:
                maps[board_type].update(direct)
                continue
        except Exception as exc:
            last_error = exc
        if loader is None:
            warnings.append(f"同花顺{board_type}分类接口在当前 AKShare 版本不可用")
        loaders_to_try = [(loader, classification_source)]
        if fallbacks.get(board_type) is not None:
            loaders_to_try.append((fallbacks[board_type], "AKShare板块分类回退"))
        for current_loader, current_source in loaders_to_try:
            if current_loader is None:
                continue
            for attempt in range(2):
                try:
                    frame = current_loader()
                    if frame is None or frame.empty:
                        raise ValueError("返回空分类")
                    for _, row in frame.iterrows():
                        name = text(row_value(row, "name", "板块", "板块名称", "行业", "行业名称"))
                        code = text(row_value(row, "code", "代码", "板块代码"))
                        if name and code:
                            maps[board_type][normalize_sector_name(name)] = {
                                "name": name,
                                "code": code,
                                "type": board_type,
                                "source": current_source,
                            }
                    break
                except Exception as exc:
                    last_error = exc
                    if attempt == 0:
                        time.sleep(0.25)
            if maps[board_type]:
                break
        if not maps[board_type] and last_error:
            warnings.append(f"同花顺{board_type}分类暂不可用: {last_error}")
    for board_type, board_map in maps.items():
        for item in board_map.values():
            THS_BOARD_INDEX[ths_board_id(board_type, item["code"])] = item
    persist_local_board_snapshot()
    THS_BOARD_CACHE = (now, maps, warnings)
    return maps, warnings


def ths_board_match(name, board_type, maps):
    normalized = normalize_sector_name(name)
    exact = maps.get(board_type, {}).get(normalized)
    if exact:
        return exact
    for candidate, item in maps.get(board_type, {}).items():
        if len(candidate) >= 2 and (candidate in normalized or normalized in candidate):
            return item
    return None


def ths_board_id(board_type, code):
    prefix = "industry" if board_type == "行业" else "concept"
    return f"ths_{prefix}_{code}"


def ths_request_headers():
    """Build the dynamic THS cookie used by q.10jqka.com.cn."""
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                      "(KHTML, like Gecko) Chrome/122.0 Safari/537.36",
        "Referer": "https://q.10jqka.com.cn/",
    }
    try:
        from akshare.datasets import get_ths_js
        import py_mini_racer
        context = py_mini_racer.MiniRacer()
        with open(get_ths_js("ths.js"), encoding="utf-8") as js_file:
            context.eval(js_file.read())
        headers["Cookie"] = f"v={context.call('v')}"
    except Exception:
        pass
    return headers


def load_ths_board_names_direct(board_type):
    """Read the complete THS category link list from its category page."""
    category_url = (
        "https://q.10jqka.com.cn/gn/detail/code/307822/"
        if board_type == "概念"
        else "https://q.10jqka.com.cn/thshy/detail/code/881272/"
    )
    prefix = "gn" if board_type == "概念" else "thshy"
    response = requests.get(category_url, headers=ths_request_headers())
    response.raise_for_status()
    response.encoding = response.apparent_encoding or "utf-8"
    pattern = re.compile(
        rf'href=["\']/({prefix})/detail/code/([^/"\']+)/?["\'][^>]*>(.*?)</a>',
        re.IGNORECASE | re.DOTALL,
    )
    result = {}
    for match in pattern.finditer(response.text):
        name = re.sub(r"<[^>]+>", "", unescape(match.group(3))).strip()
        code = text(match.group(2))
        if name and code and len(name) <= 80:
            result[normalize_sector_name(name)] = {
                "name": name,
                "code": code,
                "type": board_type,
                "source": "同花顺",
            }
    return result


def ths_stock_symbol(code):
    """Convert a six digit Tonghuashun constituent code to our full symbol."""
    value = text(code)
    if not value:
        return None
    match = re.search(r"(\d{6})", value)
    if not match:
        return None
    number_code = match.group(1)
    if number_code.startswith("6"):
        market = "sh"
    elif number_code.startswith(("8", "4")):
        market = "bj"
    else:
        market = "sz"
    return market + number_code


def normalize_ths_members(frame, classification_source="同花顺"):
    """Normalize THS/EM constituent tables to the fields used by leader scoring."""
    if frame is None or frame.empty:
        return pd.DataFrame()
    source = frame.copy()
    aliases = {
        "raw_code": ("代码", "股票代码", "证券代码", "code", "symbol"),
        "name": ("名称", "股票名称", "证券简称", "股票简称", "name"),
        "trade": ("最新价", "当前价", "现价", "trade", "price"),
        "changepercent": ("涨跌幅", "涨跌幅(%)", "changepercent", "change"),
        "amount": ("成交额", "成交金额", "amount"),
        "turnoverratio": ("换手率", "turnoverratio", "换手"),
        "per": ("市盈率-动态", "动态市盈率", "市盈率", "per", "PE"),
        "pb": ("市净率", "pb", "PB"),
        "mktcap": ("总市值", "总市值(元)", "mktcap", "市值"),
    }

    def pick(row, names):
        for name in names:
            if name in row.index:
                value = clean(row[name])
                if value is not None and value != "":
                    return value
        return None

    rows = []
    for _, row in source.iterrows():
        raw_code = pick(row, aliases["raw_code"])
        symbol = ths_stock_symbol(raw_code)
        if not symbol:
            raw_symbol = text(raw_code)
            if raw_symbol and re.fullmatch(r"(?:sh|sz|bj)\d{6}", raw_symbol.lower()):
                symbol = raw_symbol.lower()
        if not symbol:
            continue
        code = symbol[2:]
        rows.append({
            "symbol": symbol,
            "code": code,
            "name": text(pick(row, aliases["name"])),
            "trade": number(pick(row, aliases["trade"])),
            "changepercent": number(pick(row, aliases["changepercent"])),
            "amount": amount_number(pick(row, aliases["amount"])),
            "turnoverratio": number(pick(row, aliases["turnoverratio"])),
            "per": number(pick(row, aliases["per"])),
            "pb": number(pick(row, aliases["pb"])),
            "mktcap": amount_number(pick(row, aliases["mktcap"])),
            "_classificationSource": classification_source,
        })
    if not rows:
        return pd.DataFrame()
    return pd.DataFrame(rows).drop_duplicates(subset=["symbol"])


def load_ths_members(board_id, refresh=False):
    """Load constituents for a ths_* board, with HTTP and AKShare fallbacks."""
    cached = THS_MEMBER_CACHE.get(board_id)
    # Board constituents are a local low-frequency snapshot. Detail refreshes
    # only refresh quotes and minute data, never the board membership itself.
    if cached:
        return cached[1].copy()
    metadata = THS_BOARD_INDEX.get(board_id)
    if not metadata:
        raise ValueError("同花顺板块元数据不存在: " + board_id)
    board_type = metadata.get("type")
    code = text(metadata.get("code"))
    name = text(metadata.get("name"))
    classification_source = text(metadata.get("source")) or "同花顺"
    frames = []

    # The official THS detail table is the preferred classification source.
    detail_url = (
        f"https://q.10jqka.com.cn/gn/detail/code/{code}/"
        if board_type == "概念"
        else f"https://q.10jqka.com.cn/thshy/detail/code/{code}/"
    )
    if classification_source == "同花顺":
        try:
            response = requests.get(detail_url, headers=ths_request_headers())
            response.raise_for_status()
            response.encoding = response.apparent_encoding or "utf-8"
            for table in pd.read_html(StringIO(response.text)):
                normalized = normalize_ths_members(table, classification_source)
                if not normalized.empty:
                    frames.append(normalized)
                    break
        except Exception:
            pass

    # AKShare's EM constituent endpoints use the same THS board names and are
    # considerably more stable than scraping the page, so use them next.
    if not frames:
        loader = (getattr(ak, "stock_board_concept_cons_em", None)
                  if board_type == "概念"
                  else getattr(ak, "stock_board_industry_cons_em", None))
        if loader is not None:
            try:
                normalized = normalize_ths_members(loader(symbol=name), classification_source)
                if not normalized.empty:
                    frames.append(normalized)
            except Exception:
                pass

    if frames:
        result = pd.concat(frames, ignore_index=True).drop_duplicates("symbol")
        THS_MEMBER_CACHE[board_id] = (time.time(), result.copy())
        SECTOR_MEMBER_CACHE[board_id] = (time.time(), result.copy())
        index_board_members(board_id, result)
        persist_local_board_snapshot()
        return result
    if cached:
        return cached[1].copy()
    raise RuntimeError(f"同花顺板块成分数据暂不可用: {name}")


def load_board_catalog(refresh=False):
    cache_key = "board-catalog"
    if not refresh:
        cached = cache_get(cache_key, 45)
        if cached:
            return cached
    load_local_board_snapshot()
    if not refresh and THS_BOARD_INDEX:
        local_boards = []
        for board_id, metadata in THS_BOARD_INDEX.items():
            member_cache = THS_MEMBER_CACHE.get(board_id)
            company_count = len(member_cache[1]) if member_cache else 0
            local_boards.append({
                "id": board_id,
                "name": metadata.get("name"),
                "type": metadata.get("type", "概念"),
                "companyCount": company_count,
                "changePercent": 0,
                "totalVolume": None,
                "totalAmount": 0,
                "netInflow": None,
                "netInflowRatio": None,
                "leaderCode": None,
                "leaderName": None,
                "leaderPrice": None,
                "leaderChangePercent": 0,
                "score": 50,
                "status": "观察",
                "classificationSource": metadata.get("source", "同花顺本地快照"),
            })
        if local_boards:
            return cache_put(cache_key, {
                "available": True,
                "boards": local_boards,
                "warnings": ["使用本地板块与成分股快照，行情字段将在详情中实时更新"],
                "fetchedAt": dt.datetime.now().isoformat(timespec="seconds"),
            })
    boards = []
    warnings = []
    matched_ths_ids = set()
    ths_maps, ths_warnings = load_ths_board_maps(refresh=refresh)
    warnings.extend(ths_warnings)
    for indicator, board_type in (("新浪行业", "行业"), ("概念", "概念")):
        try:
            frame = ak.stock_sector_spot(indicator=indicator)
            for _, row in frame.iterrows():
                board = board_summary(row, board_type)
                legacy_id = board.get("id")
                ths = ths_board_match(board["name"], board_type, ths_maps)
                if ths:
                    board["id"] = ths_board_id(board_type, ths["code"])
                    board["name"] = ths["name"]
                    board["classificationSource"] = ths.get("source", "同花顺")
                    THS_BOARD_INDEX[board["id"]] = ths
                    if legacy_id:
                        LEGACY_BOARD_INDEX[legacy_id] = board["id"]
                        ths["legacyId"] = legacy_id
                    matched_ths_ids.add(board["id"])
                else:
                    board["classificationSource"] = "AKShare / 新浪"
                boards.append(board)
        except Exception as exc:
            warnings.append(f"{board_type}板块目录暂不可用: {exc}")

    # Keep THS-only boards searchable even when the legacy Sina spot endpoint
    # does not return a corresponding row. Their market metrics are filled in
    # after loading constituents, while the classification remains THS.
    for board_type, board_map in ths_maps.items():
        for item in board_map.values():
            board_id = ths_board_id(board_type, item["code"])
            THS_BOARD_INDEX[board_id] = item
            if board_id in matched_ths_ids or any(board.get("id") == board_id for board in boards):
                continue
            boards.append({
                "id": board_id,
                "name": item["name"],
                "type": board_type,
                "companyCount": 0,
                "changePercent": 0,
                "totalVolume": None,
                "totalAmount": 0,
                "netInflow": None,
                "netInflowRatio": None,
                "leaderCode": None,
                "leaderName": None,
                "leaderPrice": None,
                "leaderChangePercent": 0,
                "score": 50,
                "status": "观察",
                "classificationSource": item.get("source", "同花顺"),
            })
    existing_ids = {item.get("id") for item in boards}
    for board_id, metadata in THS_BOARD_INDEX.items():
        if (not str(board_id).startswith("ths_industry_local_")
                or board_id in existing_ids):
            continue
        member_cache = THS_MEMBER_CACHE.get(board_id)
        boards.append({
            "id": board_id,
            "name": metadata.get("name"),
            "type": "行业",
            "companyCount": len(member_cache[1]) if member_cache else 0,
            "changePercent": 0,
            "totalVolume": None,
            "totalAmount": 0,
            "netInflow": None,
            "netInflowRatio": None,
            "leaderCode": None,
            "leaderName": None,
            "leaderPrice": None,
            "leaderChangePercent": 0,
            "score": 50,
            "status": "观察",
            "classificationSource": metadata.get("source", "同花顺三级行业"),
        })
    payload = {
        "available": bool(boards),
        "boards": boards,
        "warnings": warnings,
        "fetchedAt": dt.datetime.now().isoformat(timespec="seconds"),
    }
    return cache_put(cache_key, payload)


def search_sectors(keyword, refresh=False):
    normalized_keyword = normalize_sector_name(keyword)
    if not normalized_keyword:
        return {"results": [], "warnings": [], "fetchedAt": dt.datetime.now().isoformat(timespec="seconds")}
    catalog = load_board_catalog(refresh=refresh)
    matches = []
    for board in catalog.get("boards", []):
        normalized_name = normalize_sector_name(board["name"])
        if normalized_keyword not in normalized_name and normalized_name not in normalized_keyword:
            continue
        exact = normalized_name == normalized_keyword
        prefix = normalized_name.startswith(normalized_keyword)
        search_rank = 0 if exact else 1 if prefix else 2
        local_industry_rank = 0 if (
            board.get("type") == "行业"
            and str(board.get("id", "")).startswith("ths_industry_local_")
        ) else 1
        matches.append((
            search_rank,
            local_industry_rank,
            -float(board.get("score") or 0),
            len(normalized_name),
            board,
        ))
    matches.sort(key=lambda item: (item[0], item[1], item[2], item[3]))
    return {
        "results": [item[4] for item in matches[:12]],
        "warnings": catalog.get("warnings", []),
        "fetchedAt": catalog.get("fetchedAt"),
    }


def sector_flow_row(flow_frame, sector_name):
    if flow_frame is None or flow_frame.empty:
        return None
    target = normalize_sector_name(sector_name)
    for _, row in flow_frame.iterrows():
        candidate = normalize_sector_name(row_value(row, "行业", "板块"))
        if candidate == target or (len(candidate) >= 2 and (candidate in target or target in candidate)):
            return row
    return None


def load_sector_list(refresh=False):
    global SECTOR_LIST_LAST_GOOD
    cache_key = "sector-list"
    cached = cache_get(cache_key, 45)
    if not refresh and cached:
        return cached

    warnings = []
    ths_maps, ths_warnings = load_ths_board_maps(refresh=refresh)
    warnings.extend(ths_warnings)
    sector_frame = None
    last_error = None
    for attempt in range(3):
        try:
            sector_frame = ak.stock_sector_spot(indicator="新浪行业")
            if sector_frame is None or sector_frame.empty:
                raise ValueError("行业板块接口返回空数据")
            break
        except Exception as exc:
            last_error = exc
            if attempt < 2:
                time.sleep(0.35 * (attempt + 1))
    if sector_frame is None or sector_frame.empty:
        if cached:
            return cached
        if SECTOR_LIST_LAST_GOOD:
            fallback = dict(SECTOR_LIST_LAST_GOOD)
            fallback["warnings"] = list(dict.fromkeys(
                list(fallback.get("warnings", []))
                + [f"行业行情本次刷新失败，展示最近成功数据: {last_error}"]
            ))
            return cache_put(cache_key, fallback)
        catalog = load_board_catalog(refresh=False)
        fallback_sectors = [
            board for board in catalog.get("boards", [])
            if board.get("type") == "行业"
        ]
        if fallback_sectors:
            fallback = {
                "available": True,
                "flowAvailable": False,
                "sectors": fallback_sectors,
                "source": "同花顺行业分类目录（行业实时行情暂不可用）",
                "warnings": [f"行业实时行情暂不可用，已切换同花顺分类目录: {last_error}"],
                "fetchedAt": dt.datetime.now().isoformat(timespec="seconds"),
            }
            SECTOR_LIST_LAST_GOOD = fallback
            return cache_put(cache_key, fallback)
        raise RuntimeError(f"行业板块数据连续请求失败: {last_error}") from last_error

    flow_frame = None
    try:
        flow_frame = ak.stock_fund_flow_industry(symbol="即时")
    except Exception:
        warnings.append("行业资金流数据源当前超时，资金列暂不参与主线评分")

    amount_values = [number(value) or 0 for value in sector_frame.get("总成交额", [])]
    log_amounts = [math.log10(max(value, 1)) for value in amount_values]
    min_log = min(log_amounts) if log_amounts else 0
    max_log = max(log_amounts) if log_amounts else 1
    sectors = []
    for index, (_, row) in enumerate(sector_frame.iterrows()):
        name = text(row_value(row, "板块"))
        total_amount = number(row_value(row, "总成交额")) or 0
        activity = 50.0 if max_log == min_log else (
            (math.log10(max(total_amount, 1)) - min_log) / (max_log - min_log) * 100
        )
        flow_row = sector_flow_row(flow_frame, name)
        net_inflow = amount_number(row_value(flow_row, "净额", "净流入")) if flow_row is not None else None
        net_ratio = net_inflow / total_amount * 100 if net_inflow is not None and total_amount > 0 else None
        board = board_summary(
            row, "行业", net_inflow=net_inflow, net_ratio=net_ratio, activity=activity)
        ths = ths_board_match(board["name"], "行业", ths_maps)
        if ths:
            board["id"] = ths_board_id("行业", ths["code"])
            board["name"] = ths["name"]
            board["classificationSource"] = ths.get("source", "同花顺")
            THS_BOARD_INDEX[board["id"]] = ths
        else:
            board["classificationSource"] = "AKShare / 新浪"
        sectors.append(board)
    sectors.sort(key=lambda item: item["score"], reverse=True)
    # Keep locally discovered THS third-level industries visible alongside
    # live board quotes. Their quote fields are refreshed in the detail view.
    existing_ids = {item.get("id") for item in sectors}
    for board_id, metadata in THS_BOARD_INDEX.items():
        if not str(board_id).startswith("ths_industry_local_") or board_id in existing_ids:
            continue
        member_cache = THS_MEMBER_CACHE.get(board_id)
        sectors.append({
            "id": board_id,
            "name": metadata.get("name"),
            "type": "行业",
            "companyCount": len(member_cache[1]) if member_cache else 0,
            "changePercent": 0,
            "totalVolume": None,
            "totalAmount": 0,
            "netInflow": None,
            "netInflowRatio": None,
            "leaderCode": None,
            "leaderName": None,
            "leaderPrice": None,
            "leaderChangePercent": 0,
            "score": 50,
            "status": "观察",
            "classificationSource": metadata.get("source", "同花顺三级行业"),
        })
    sectors.sort(key=lambda item: item["score"], reverse=True)
    payload = {
        "available": bool(sectors),
        "flowAvailable": any(item["netInflow"] is not None for item in sectors),
        "sectors": sectors,
        "source": "AKShare / 新浪行业行情；同花顺行业资金流（可降级）",
        "warnings": warnings,
        "fetchedAt": dt.datetime.now().isoformat(timespec="seconds"),
    }
    SECTOR_LIST_LAST_GOOD = payload
    return cache_put(cache_key, payload)


def load_sector_members(sector_id, refresh=False):
    legacy_mapping = LEGACY_BOARD_INDEX.get(str(sector_id))
    if not legacy_mapping and not str(sector_id).startswith("ths_"):
        try:
            load_board_catalog()
            legacy_mapping = LEGACY_BOARD_INDEX.get(str(sector_id))
        except Exception:
            pass
    if legacy_mapping:
        try:
            return load_ths_members(legacy_mapping, refresh=refresh)
        except Exception:
            pass
    if str(sector_id).startswith("ths_"):
        try:
            return load_ths_members(sector_id, refresh=refresh)
        except Exception as ths_error:
            # A THS board can still be represented by the legacy endpoint. Use
            # its display name as a final compatibility fallback.
            metadata = THS_BOARD_INDEX.get(str(sector_id)) or {}
            legacy_name = text(metadata.get("name"))
            if legacy_name:
                try:
                    legacy = ak.stock_sector_detail(sector=legacy_name)
                    if legacy is not None and not legacy.empty:
                        legacy = legacy.copy()
                        legacy["_classificationSource"] = "AKShare / 新浪回退"
                        SECTOR_MEMBER_CACHE[sector_id] = (time.time(), legacy.copy())
                        return legacy
                except Exception:
                    pass
            cached = SECTOR_MEMBER_CACHE.get(sector_id)
            if cached:
                return cached[1].copy()
            raise ths_error
    cached = SECTOR_MEMBER_CACHE.get(sector_id)
    if cached and time.time() - cached[0] <= 180:
        return cached[1].copy()

    last_error = None
    for attempt in range(3):
        try:
            frame = ak.stock_sector_detail(sector=sector_id)
            if frame is None or frame.empty:
                raise ValueError("板块成分接口返回空数据")
            SECTOR_MEMBER_CACHE[sector_id] = (time.time(), frame.copy())
            return frame
        except Exception as exc:
            last_error = exc
            if attempt < 2:
                time.sleep(0.35 * (attempt + 1))

    if cached:
        return cached[1].copy()
    raise RuntimeError(f"板块成分数据连续请求失败: {last_error}") from last_error


def load_limit_up_status(full_codes):
    """Load today's limit-up pool once and keep sealing quality beside each member."""
    requested = normalized_full_codes(full_codes)
    if not requested:
        return {}
    today = dt.datetime.now(SHANGHAI_ZONE).strftime("%Y%m%d")
    cached = LIMIT_UP_CACHE.get(today)
    if cached and time.time() - cached[0] <= 20:
        return {code: cached[1][code] for code in requested if code in cached[1]}
    try:
        frame = ak.stock_zt_pool_em(date=today)
    except Exception:
        LIMIT_UP_CACHE[today] = (time.time(), {})
        return {}
    result = {}
    if frame is not None and not frame.empty:
        for _, row in frame.iterrows():
            raw_code = text(row_value(row, "代码", "股票代码", "code"))
            if not raw_code:
                continue
            candidates = {raw_code.lower()}
            if raw_code.isdigit():
                candidates.update({f"sh{raw_code}", f"sz{raw_code}", f"bj{raw_code}"})
            full_code = next((code for code in candidates if code in requested), None)
            if not full_code:
                continue
            seal_amount = amount_number(row_value(row, "封板资金", "封单资金"))
            breakout_count = number(row_value(row, "炸板次数", "炸板"))
            continuous_boards = number(row_value(row, "连板数", "连续涨停"))
            change_percent = number(row_value(row, "涨跌幅"))
            result[full_code] = {
                "limitUp": True,
                "sealAmount": seal_amount,
                "breakoutCount": int(breakout_count) if breakout_count is not None else None,
                "continuousBoards": int(continuous_boards) if continuous_boards is not None else None,
                "changePercentFromLimitPool": change_percent,
            }
    LIMIT_UP_CACHE[today] = (time.time(), result)
    return {code: result[code] for code in requested if code in result}


def resolve_sector(identifier):
    identifier = str(identifier)
    mapped_legacy_id = LEGACY_BOARD_INDEX.get(identifier)
    if mapped_legacy_id:
        catalog = load_board_catalog()
        for board in catalog.get("boards", []):
            if board.get("id") == mapped_legacy_id:
                return board
    if identifier.startswith("ths_"):
        # Loading the catalog populates THS_BOARD_INDEX on a fresh process.
        catalog = load_board_catalog()
        for board in catalog.get("boards", []):
            if board.get("id") == identifier:
                return board
        metadata = THS_BOARD_INDEX.get(identifier)
        if metadata:
            return {
                "id": identifier,
                "name": metadata.get("name"),
                "type": metadata.get("type", "板块"),
                "companyCount": 0,
                "changePercent": 0,
                "totalAmount": 0,
                "score": 50,
                "status": "观察",
                "classificationSource": "同花顺",
            }
    if str(identifier).lower().startswith("gn_"):
        catalog = load_board_catalog()
        for sector in catalog.get("boards", []):
            if sector["id"] == identifier:
                return sector
        mapped_legacy_id = LEGACY_BOARD_INDEX.get(identifier)
        if mapped_legacy_id:
            for sector in catalog.get("boards", []):
                if sector.get("id") == mapped_legacy_id:
                    return sector
    legacy_keywords = globals().get("SECTOR_KEYWORDS", {}).get(identifier, [])
    if legacy_keywords:
        catalog = load_board_catalog()
        matches = []
        for sector in catalog.get("boards", []):
            candidate = normalize_sector_name(sector.get("name"))
            for index, keyword in enumerate(legacy_keywords):
                normalized_keyword = normalize_sector_name(keyword)
                if candidate == normalized_keyword:
                    matches.append((index, 0, sector))
                    break
                if candidate and normalized_keyword and (
                        candidate in normalized_keyword or normalized_keyword in candidate):
                    matches.append((index, 1, sector))
                    break
        if matches:
            matches.sort(key=lambda item: (item[0], item[1], len(item[2].get("name") or "")))
            return matches[0][2]
    try:
        listing = load_sector_list().get("sectors", [])
    except Exception:
        listing = []
    normalized = normalize_sector_name(identifier)
    for sector in listing:
        if sector["id"] == identifier or sector["name"] == identifier:
            return sector
    for sector in listing:
        candidate = normalize_sector_name(sector["name"])
        if candidate == normalized or (len(candidate) >= 2 and (candidate in normalized or normalized in candidate)):
            return sector
    for sector in load_board_catalog().get("boards", []):
        candidate = normalize_sector_name(sector["name"])
        if sector["id"] == identifier or candidate == normalized:
            return sector
    return None


SECTOR_KEYWORDS = {
    "new_jxhy": ["机械", "机器人", "自动化", "通用设备", "专用设备", "工业母机"],
    "new_yqyb": ["仪器", "仪表"],
    "new_dzqj": ["半导体", "元件", "电子器件", "芯片"],
    "new_dzxx": ["计算机", "软件", "通信", "电子信息", "互联网"],
    "new_dqhy": ["电气", "电器", "电网"],
    "new_fdsb": ["电源", "发电设备", "光伏", "风电"],
    "new_qczz": ["汽车", "汽车零部件"],
    "new_swzz": ["生物", "制药", "医药"],
    "new_ylqx": ["医疗器械", "医疗设备"],
    "new_jrhy": ["银行", "证券", "保险", "金融"],
    "new_ljhy": ["白酒", "酿酒"],
    "new_nlmy": ["农业", "农林牧渔", "养殖"],
    "new_hghy": ["化工", "化学"],
    "new_ysjs": ["有色", "金属"],
    "new_mthy": ["煤炭"],
    "new_syhy": ["石油"],
    "new_dlhy": ["电力"],
    "new_fdc": ["房地产"],
    "new_jzjc": ["建筑", "建材"],
    "new_sphy": ["食品", "饮料"],
    "new_cmyl": ["传媒", "广告", "影视"],
}


def load_ths_stock_field(code):
    """Read THS' three-level industry and complete peer list for one stock."""
    cached = THS_STOCK_FIELD_CACHE.get(code)
    if cached:
        return list(cached[0]), cached[1].copy()
    try:
        response = requests.get(
            f"https://basic.10jqka.com.cn/{code}/field.html",
            headers=ths_request_headers(),
        )
        response.raise_for_status()
        page = response.content.decode("gbk", errors="replace")
        match = re.search(
            r'<p[^>]*class=["\'][^"\']*threecate[^"\']*["\'][^>]*>.*?'
            r'<span[^>]*class=["\'][^"\']*tip[^"\']*["\'][^>]*>(.*?)</span>',
            page,
            re.IGNORECASE | re.DOTALL,
        )
        if not match:
            return [], pd.DataFrame()
        industry_path = unescape(re.sub(r"<[^>]+>", "", match.group(1)))
        industry_path = re.split(r"[（(]\s*\u5171", industry_path, maxsplit=1)[0]
        terms = [
            item.strip()
            for item in re.split(r"\s*(?:--|>|/|－)\s*", industry_path)
            if item.strip()
        ]
        data_match = re.search(
            r'id=["\']fieldsChartData["\'][^>]*value=(?P<quote>["\'])(.*?)(?P=quote)',
            page,
            re.IGNORECASE | re.DOTALL,
        )
        rows = []
        if data_match:
            periods = json.loads(unescape(data_match.group(2)))
            latest_period = max(periods) if periods else None
            for item in periods.get(latest_period, []) if latest_period else []:
                if not isinstance(item, list) or len(item) < 2:
                    continue
                symbol = ths_stock_symbol(item[0])
                if not symbol:
                    continue
                rows.append({
                    "symbol": symbol,
                    "code": symbol[2:],
                    "name": text(item[1]),
                    "trade": None,
                    "changepercent": None,
                    "amount": None,
                    "turnoverratio": None,
                    "per": None,
                    "pb": None,
                    "mktcap": None,
                    "_classificationSource": "同花顺三级行业",
                })
        members = pd.DataFrame(rows).drop_duplicates("symbol") if rows else pd.DataFrame()
        THS_STOCK_FIELD_CACHE[code] = (list(terms), members.copy())
        return terms, members
    except Exception:
        return [], pd.DataFrame()


def load_ths_stock_industry_terms(code):
    return load_ths_stock_field(code)[0]


def create_local_stock_industry_board(full_code):
    """Create one stable local THS industry board directly from the stock page."""
    terms, members = load_ths_stock_field(full_code[2:])
    if not terms or members.empty:
        return None
    sector_name = re.sub(r"[ⅠⅡⅢⅣⅤⅥⅦⅧⅨⅩ]+$", "", terms[-1]).strip() or terms[-1]
    digest = hashlib.sha1(normalize_sector_name(sector_name).encode("utf-8")).hexdigest()[:12]
    board_id = f"ths_industry_local_{digest}"
    members = overlay_realtime_quotes(members)
    THS_BOARD_INDEX[board_id] = {
        "name": sector_name,
        "code": f"local:{digest}",
        "type": "行业",
        "source": "同花顺三级行业",
    }
    THS_MEMBER_CACHE[board_id] = (time.time(), members.copy())
    SECTOR_MEMBER_CACHE[board_id] = (time.time(), members.copy())
    index_board_members(board_id, members)
    persist_local_board_snapshot()
    sector = local_board_summary(board_id)
    if sector:
        sector["companyCount"] = int(len(members))
    return sector


def stock_industry_terms(code):
    terms = load_ths_stock_industry_terms(code)
    try:
        frame = ak.stock_industry_change_cninfo(
            symbol=code,
            start_date="20000101",
            end_date=dt.date.today().strftime("%Y%m%d"),
        )
        for column in ("行业门类", "行业次类", "行业大类", "行业中类"):
            if column in frame.columns:
                terms.extend(text(value) for value in frame[column].tail(12) if text(value))
    except Exception:
        pass
    if not terms:
        try:
            profile = ak.stock_profile_cninfo(symbol=code)
            if profile is not None and not profile.empty:
                industry = text(row_value(profile.iloc[0], "所属行业", "行业"))
                if industry:
                    terms.append(industry)
        except Exception:
            pass
    return list(dict.fromkeys(terms))


def stock_sector_match_score(sector, terms):
    """Score one canonical board against the stock's official industries."""
    score = 200 if text(sector.get("type")) == "行业" else 0
    core = normalize_sector_name(sector.get("name"))
    if not core:
        return score
    generic_terms = {
        "其他", "制造", "工业", "资本品", "主要消费", "信息技术",
    }
    for index, term in enumerate(terms):
        normalized_term = normalize_sector_name(term)
        # Empty normalized terms used to match every board because
        # ``"" in core`` is true, making the result depend on list order.
        if not normalized_term or normalized_term in generic_terms:
            continue
        order_bonus = max(0, 60 - index * 2)
        if core == normalized_term:
            score += 2000 + len(core) * 10 + order_bonus
        elif core in normalized_term:
            score += 900 + int(len(core) / len(normalized_term) * 100) + order_bonus
        elif normalized_term in core:
            score += 650 + int(len(normalized_term) / len(core) * 100) + order_bonus
    return score


def remember_primary_board(full_code, sector):
    STOCK_SECTOR_CACHE[full_code] = sector
    board_id = sector.get("id")
    if board_id and board_id in THS_BOARD_INDEX:
        if STOCK_PRIMARY_BOARD.get(full_code) != board_id:
            STOCK_PRIMARY_BOARD[full_code] = board_id
            persist_local_board_snapshot()
    return sector


def resolve_stock_sector(full_code, refresh=False):
    full_code = full_code.lower()
    if full_code in STOCK_SECTOR_CACHE:
        return STOCK_SECTOR_CACHE[full_code]
    load_local_board_snapshot()
    code = full_code[2:]

    primary_board_id = STOCK_PRIMARY_BOARD.get(full_code)
    if primary_board_id:
        primary = local_board_summary(primary_board_id)
        if primary:
            return remember_primary_board(full_code, primary)

    # Preferred path: use the reverse index built from the local board snapshot.
    local_candidates = [
        local_board_summary(board_id)
        for board_id in STOCK_BOARD_INDEX.get(full_code, [])
    ]
    local_candidates = [sector for sector in local_candidates if sector]
    if local_candidates:
        if len(local_candidates) == 1:
            return remember_primary_board(full_code, local_candidates[0])
        terms = stock_industry_terms(code)
        sector = max(
            local_candidates,
            key=lambda item: stock_sector_match_score(item, terms),
        )
        return remember_primary_board(full_code, sector)

    # The stock's THS field page contains its most specific industry and the
    # complete peer list in one response. Persist that as the canonical local
    # board so subsequent detail/timed refreshes only request market quotes.
    direct_sector = create_local_stock_industry_board(full_code)
    if direct_sector:
        return remember_primary_board(full_code, direct_sector)

    # If this stock has not been stored yet, match its official industry terms
    # against the local THS catalog without requesting every board's members.
    terms = stock_industry_terms(code)
    catalog_sectors = [
        local_board_summary(board_id)
        for board_id in THS_BOARD_INDEX
    ]
    catalog_sectors = [sector for sector in catalog_sectors if sector]
    matched_catalog = sorted(
        (
            sector for sector in catalog_sectors
            if stock_sector_match_score(sector, terms) > 200
        ),
        key=lambda item: stock_sector_match_score(item, terms),
        reverse=True,
    )
    # A name match is only a candidate. Confirm actual membership before
    # persisting the stock-to-board mapping.
    for sector in matched_catalog[:6]:
        try:
            members = load_sector_members(sector["id"], refresh=False)
            codes = members["code"].astype(str).str.extract(r"(\d{6})", expand=False)
            if (codes == code).any():
                return remember_primary_board(full_code, sector)
        except Exception:
            continue

    try:
        sectors = load_sector_list(refresh=False).get("sectors", [])
    except Exception:
        sectors = []
    ordered = sorted(
        sectors,
        key=lambda item: stock_sector_match_score(item, terms),
        reverse=True,
    )
    likely_sectors = [
        sector for sector in ordered
        if stock_sector_match_score(sector, terms) > 200
    ][:6]
    for sector in likely_sectors:
        try:
            frame = load_sector_members(sector["id"], refresh=False)
            codes = frame["code"].astype(str).str.extract(r"(\d{6})", expand=False)
            if (codes == code).any():
                return remember_primary_board(full_code, sector)
        except Exception:
            continue
    return None


def metric_window(frame, minutes):
    if frame.empty:
        return None, None
    latest = number(frame.iloc[-1]["close"])
    reference_index = max(0, len(frame) - minutes - 1)
    reference = number(frame.iloc[reference_index]["close"])
    window = frame.tail(minutes)
    first_price = number(window.iloc[0]["open"])
    high = pd.to_numeric(window["high"], errors="coerce").max()
    low = pd.to_numeric(window["low"], errors="coerce").min()
    momentum = (latest - reference) / reference * 100 if latest is not None and reference else None
    amplitude = (high - low) / first_price * 100 if first_price and pd.notna(high) and pd.notna(low) else None
    return momentum, amplitude


def merge_realtime_quote_into_minutes(frame, realtime_price=None, realtime_time=None):
    """Overlay the latest quote without allowing it to replace the source history."""
    price = number(realtime_price)
    if price is None or price <= 0 or frame.empty:
        return frame, None
    quote_at = pd.to_datetime(
        str(realtime_time or ""), format="%Y%m%d%H%M%S", errors="coerce")
    if pd.isna(quote_at):
        return frame, None
    latest_at = frame.iloc[-1]["day"]
    if quote_at.date() != latest_at.date() or quote_at < latest_at - dt.timedelta(minutes=5):
        return frame, None
    result = frame.copy()
    if quote_at.floor("min") > latest_at.floor("min"):
        result = pd.concat([result, pd.DataFrame([{
            "day": quote_at.floor("min"),
            "open": price,
            "high": price,
            "low": price,
            "close": price,
            "volume": 0,
            "amount": 0,
        }])], ignore_index=True)
    else:
        index = result.index[-1]
        high = number(result.at[index, "high"])
        low = number(result.at[index, "low"])
        result.at[index, "high"] = max(high, price) if high is not None else price
        result.at[index, "low"] = min(low, price) if low is not None else price
        result.at[index, "close"] = price
    return result, quote_at


def load_tencent_minute_frame(full_code):
    response = requests.get(
        "http://web.ifzq.gtimg.cn/appstock/app/minute/query",
        params={"code": full_code},
        headers={"Referer": "https://gu.qq.com/"},
    )
    response.raise_for_status()
    payload = response.json()
    stock_payload = (payload.get("data") or {}).get(full_code) or {}
    minute_payload = stock_payload.get("data") or {}
    raw_points = minute_payload.get("data") or []
    if not raw_points:
        raise ValueError("腾讯分钟行情返回空数据")
    trading_date = text(minute_payload.get("date") or stock_payload.get("date"))
    if not trading_date or not re.fullmatch(r"\d{8}", trading_date):
        trading_date = dt.datetime.now(SHANGHAI_ZONE).strftime("%Y%m%d")

    rows = []
    previous_volume = 0.0
    previous_amount = 0.0
    for raw_point in raw_points:
        fields = str(raw_point).split()
        if len(fields) < 2 or not re.fullmatch(r"\d{4}", fields[0]):
            continue
        price = number(fields[1])
        if price is None or price <= 0:
            continue
        cumulative_volume = number(fields[2]) if len(fields) > 2 else None
        cumulative_amount = number(fields[3]) if len(fields) > 3 else None
        volume = None if cumulative_volume is None else max(0.0, cumulative_volume - previous_volume)
        amount = None if cumulative_amount is None else max(0.0, cumulative_amount - previous_amount)
        if cumulative_volume is not None:
            previous_volume = cumulative_volume
        if cumulative_amount is not None:
            previous_amount = cumulative_amount
        timestamp = pd.to_datetime(
            f"{trading_date} {fields[0][:2]}:{fields[0][2:]}",
            format="%Y%m%d %H:%M",
            errors="coerce",
        )
        if pd.isna(timestamp):
            continue
        rows.append({
            "day": timestamp,
            "open": price,
            "high": price,
            "low": price,
            "close": price,
            "volume": volume,
            "amount": amount,
        })
    frame = pd.DataFrame(rows)
    if frame.empty:
        raise ValueError("腾讯分钟行情没有有效数据点")
    return frame


def load_minute_metrics(full_code, realtime_price=None, realtime_time=None):
    try:
        frame = load_tencent_minute_frame(full_code)
    except Exception:
        # AKShare is retained as a fallback. Its Sina parser is not thread-safe
        # on Windows, so only fallback calls are serialized.
        with AKSHARE_MINUTE_LOCK:
            frame = ak.stock_zh_a_minute(symbol=full_code, period="1", adjust="")
    if frame.empty:
        raise ValueError("暂无分钟行情")
    frame = frame.copy()
    frame["day"] = pd.to_datetime(frame["day"], errors="coerce")
    frame = frame.dropna(subset=["day"]).sort_values("day")
    latest_date = frame.iloc[-1]["day"].date()
    frame = frame[frame["day"].dt.date == latest_date]
    for column in ("open", "high", "low", "close", "volume", "amount"):
        frame[column] = pd.to_numeric(frame[column], errors="coerce")
    frame, quote_at = merge_realtime_quote_into_minutes(
        frame, realtime_price, realtime_time)
    return_1m, amplitude_1m = metric_window(frame, 1)
    return_3m, amplitude_3m = metric_window(frame, 3)
    return_5m, amplitude_5m = metric_window(frame, 5)
    recent_count = min(5, len(frame))
    recent_volume = frame.tail(recent_count)["volume"].sum()
    prior = frame.iloc[max(0, len(frame) - recent_count - 10):max(0, len(frame) - recent_count)]["volume"]
    volume_ratio = None
    if not prior.empty and prior.mean() > 0:
        volume_ratio = recent_volume / (prior.mean() * recent_count)
    points = [{
        "time": row["day"].strftime("%H:%M"),
        "price": number(row["close"]),
        "volume": number(row["volume"]),
    } for _, row in frame.tail(36).iterrows()]
    return {
        "return1m": return_1m,
        "return3m": return_3m,
        "return5m": return_5m,
        "amplitude1m": amplitude_1m,
        "amplitude3m": amplitude_3m,
        "amplitude5m": amplitude_5m,
        "volumeRatio": volume_ratio,
        "volumeExpanded": bool(volume_ratio is not None and volume_ratio >= 1.5),
        "points": points,
        "minuteDataTime": (quote_at if quote_at is not None else frame.iloc[-1]["day"]).isoformat(),
    }


def eastmoney_secid(full_code):
    normalized = text(full_code)
    if not normalized or not re.fullmatch(r"(?:sh|sz|bj)\d{6}", normalized.lower()):
        return None
    normalized = normalized.lower()
    market = "1" if normalized.startswith("sh") else "0"
    return f"{market}.{normalized[2:]}"


def normalized_full_codes(full_codes):
    return {
        normalized.lower()
        for full_code in full_codes
        if (normalized := text(full_code))
        and re.fullmatch(r"(?:sh|sz|bj)\d{6}", normalized.lower())
    }


def load_sina_realtime_stock_flows(full_codes, sector_id):
    requested = normalized_full_codes(full_codes)
    if not requested or not sector_id:
        return {}
    response = requests.get(SINA_REALTIME_FLOW_URL, params={
        "page": "1",
        "num": "1000",
        "sort": "netamount",
        "asc": "0",
        "bankuai": sector_id,
    }, headers={"User-Agent": "Mozilla/5.0"})
    response.raise_for_status()
    rows = response.json()
    if not isinstance(rows, list):
        return {}
    updated_at = dt.datetime.now(SHANGHAI_ZONE)
    result = {}
    for row in rows:
        full_code = (text(row.get("symbol")) or "").lower()
        if full_code not in requested:
            continue
        net_inflow = number(row.get("netamount"))
        raw_ratio = number(row.get("ratioamount"))
        if net_inflow is None and raw_ratio is None:
            continue
        result[full_code] = {
            "mainNetInflow": net_inflow,
            "mainNetRatio": raw_ratio * 100 if raw_ratio is not None else None,
            "flowDate": updated_at.strftime("%Y-%m-%d %H:%M"),
        }
    return result


def load_eastmoney_realtime_stock_flows(full_codes):
    code_by_number = {}
    secids = []
    for full_code in full_codes:
        normalized = text(full_code)
        secid = eastmoney_secid(normalized)
        if not secid:
            continue
        normalized = normalized.lower()
        code_by_number[normalized[2:]] = normalized
        secids.append(secid)
    if not secids:
        return {}

    response = requests.get(EASTMONEY_REALTIME_FLOW_URL, params={
        "fltt": "2",
        "invt": "2",
        "ut": EASTMONEY_UT,
        "secids": ",".join(dict.fromkeys(secids)),
        "fields": "f12,f62,f184,f124",
    }, headers={"User-Agent": "Mozilla/5.0"})
    response.raise_for_status()
    payload = response.json()
    rows = (payload.get("data") or {}).get("diff") or []
    today = dt.datetime.now(SHANGHAI_ZONE).date()
    result = {}
    for row in rows:
        code = text(row.get("f12"))
        timestamp = number(row.get("f124"))
        full_code = code_by_number.get(code)
        if not full_code or timestamp is None:
            continue
        updated_at = dt.datetime.fromtimestamp(timestamp, SHANGHAI_ZONE)
        if updated_at.date() != today:
            continue
        net_inflow = number(row.get("f62"))
        net_ratio = number(row.get("f184"))
        if net_inflow is None and net_ratio is None:
            continue
        result[full_code] = {
            "mainNetInflow": net_inflow,
            "mainNetRatio": net_ratio,
            "flowDate": updated_at.strftime("%Y-%m-%d %H:%M"),
        }
    return result


def load_realtime_stock_flows(full_codes, sector_id=None):
    requested = normalized_full_codes(full_codes)
    result = {}
    # Sina's bankuai parameter only accepts its own gn_/new_ identifiers.
    # Passing a THS id waits for a timeout and can never return matching rows.
    if sector_id and not str(sector_id).startswith("ths_"):
        try:
            result.update(load_sina_realtime_stock_flows(requested, sector_id))
        except Exception:
            pass
    missing = requested.difference(result)
    if missing:
        try:
            result.update(load_eastmoney_realtime_stock_flows(missing))
        except Exception:
            pass
    return result


def load_tencent_realtime_quotes(full_codes):
    """Load current price and daily change for a fixed local leader pool."""
    requested = normalized_full_codes(full_codes)
    if not requested:
        return {}
    response = requests.get(
        "http://qt.gtimg.cn/q=" + ",".join(sorted(requested)),
        headers={"Referer": "https://finance.qq.com/"},
    )
    response.raise_for_status()
    response.encoding = "gbk"
    result = {}
    for line in response.text.splitlines():
        match = re.search(r'v_((?:sh|sz|bj)\d{6})="([^"]*)"', line, re.IGNORECASE)
        if not match:
            continue
        full_code = match.group(1).lower()
        fields = match.group(2).split("~")
        if full_code not in requested or len(fields) < 47:
            continue
        current = number(fields[3])
        previous = number(fields[4])
        change = (current - previous) / previous * 100 if current is not None and previous else None
        amount_wan = number(fields[37])
        market_cap_yi = number(fields[45])
        result[full_code] = {
            "name": text(fields[1]),
            "trade": current,
            "changepercent": change,
            "amount": amount_wan * 10000 if amount_wan is not None else None,
            "turnoverratio": number(fields[38]),
            "per": number(fields[39]),
            "mktcap": market_cap_yi * 100000000 if market_cap_yi is not None else None,
            "pb": number(fields[46]),
            "_quoteTime": text(fields[30]),
        }
    return result


def select_leader_candidates(frame, selected_code=None):
    candidates = frame.copy()
    for column in ("symbol", "name", "code", "amount", "mktcap", "changepercent", "turnoverratio", "per"):
        if column not in candidates.columns:
            candidates[column] = None
    for column in ("amount", "mktcap", "changepercent", "turnoverratio", "per"):
        candidates[column] = pd.to_numeric(candidates[column], errors="coerce")
    candidates["_liquidity"] = candidates["amount"].fillna(0).rank(pct=True) * 100
    candidates["_scale"] = candidates["mktcap"].fillna(0).rank(pct=True) * 100
    candidates["_momentum"] = candidates["changepercent"].fillna(0).rank(pct=True) * 100
    candidates["_turnover"] = candidates["turnoverratio"].fillna(0).rank(pct=True) * 100
    candidates["_profit"] = candidates["per"].apply(lambda value: 100 if pd.notna(value) and 0 < value <= 120 else 35 if pd.notna(value) and value > 0 else 0)
    candidates["_leader"] = (
        candidates["_liquidity"] * 0.30
        + candidates["_scale"] * 0.25
        + candidates["_momentum"] * 0.15
        + candidates["_turnover"] * 0.10
        + candidates["_profit"] * 0.20
    )
    ranked = candidates.sort_values("_leader", ascending=False)
    profitable = ranked[ranked["per"] > 0]
    remaining = ranked[~ranked["symbol"].isin(profitable["symbol"])]
    selected = pd.concat([profitable, remaining], ignore_index=True).head(10)
    if selected_code:
        target = candidates[candidates["symbol"].astype(str).str.lower() == selected_code.lower()]
        if not target.empty and target.iloc[0]["symbol"] not in selected["symbol"].values:
            selected = pd.concat([selected.head(9), target.head(1)], ignore_index=True)
    return selected


def load_or_create_local_leaders(sector_id, members):
    """Freeze the first screened Top10 so timed refreshes only update quotes."""
    cached = THS_LEADER_CACHE.get(sector_id)
    if cached is not None and not cached.empty:
        return cached.copy()
    leaders = select_leader_candidates(members, None)
    if str(sector_id).startswith("ths_") and not leaders.empty:
        THS_LEADER_CACHE[sector_id] = leaders.copy()
        persist_local_board_snapshot()
    return leaders


def overlay_realtime_quotes(frame):
    result = frame.copy()
    symbols = [text(value) for value in result.get("symbol", []) if text(value)]
    try:
        quotes = load_tencent_realtime_quotes(symbols)
    except Exception:
        quotes = {}
    for index, row in result.iterrows():
        full_code = (text(row_value(row, "symbol")) or "").lower()
        quote = quotes.get(full_code)
        if not quote:
            continue
        for column, value in quote.items():
            if value is not None:
                result.at[index, column] = value
    return result


def component_score(value, scale):
    return 50 if value is None else clamp(50 + value * scale)


def limit_up_strength_score(limit_quality_score, breakout_count):
    """Score a sealed limit-up without relying on unavailable minute returns."""
    quality = clamp(limit_quality_score if limit_quality_score is not None else 50)
    score = 60 + quality * 0.35
    if breakout_count is None:
        score -= 2
    elif breakout_count >= 2:
        score -= min(12, (breakout_count - 1) * 4)
    return clamp(score)


def brief_rank_percent(value):
    return "--" if value is None else f"{value:+.2f}%"


def brief_rank_amount(value):
    if value is None:
        return "--"
    absolute = abs(value)
    if absolute >= 100000000:
        return f"{absolute / 100000000:.2f}亿元"
    if absolute >= 10000:
        return f"{absolute / 10000:.1f}万元"
    return f"{absolute:.0f}元"


def build_ranking_reason(item, rank, daily_leader_change):
    reasons = [f"第{rank}名，强度{item.get('score', 0):.1f}分"]
    if item.get("limitUp"):
        quality = item.get("limitQualityScore")
        breakout_count = item.get("breakoutCount")
        reasons.append("涨停封板" + (f"，封板质量{quality:.1f}" if quality is not None else ""))
        reasons.append("零炸板" if breakout_count == 0 else (
            f"炸板{breakout_count}次" if breakout_count is not None else "炸板次数未知"))
        if item.get("sealAmount") is not None:
            reasons.append(f"封单{brief_rank_amount(item['sealAmount'])}")
    else:
        returns = [item.get("return1m"), item.get("return3m"), item.get("return5m")]
        if any(value is not None for value in returns):
            reasons.append("1/3/5分 " + "/".join(brief_rank_percent(value) for value in returns))
        volume_ratio = item.get("volumeRatio")
        if volume_ratio is not None:
            reasons.append(f"5分钟量比{volume_ratio:.2f}" + ("，明显放量" if volume_ratio >= 1.5 else ""))
    main_net = item.get("mainNetInflow")
    if main_net is not None:
        direction = "净流入" if main_net >= 0 else "净流出"
        ratio = item.get("mainNetRatio")
        reasons.append(f"主力{direction}{brief_rank_amount(main_net)}" +
                       (f"（{brief_rank_percent(ratio)}）" if ratio is not None else ""))
    relative = item.get("relativeStrength")
    if relative is not None:
        reasons.append(("领先" if relative >= 0 else "落后") +
                       f"板块均值{abs(relative):.2f}个百分点")
    daily_change = item.get("dailyChangePercent")
    if daily_change is not None and daily_leader_change is not None \
            and daily_change >= daily_leader_change - 0.01:
        reasons.append("板块内日涨幅领先")
    return "；".join(reasons[:7])


def quarter_number(report_date):
    try:
        month = int(str(report_date)[5:7])
        return {3: 1, 6: 2, 9: 3, 12: 4}.get(month)
    except (TypeError, ValueError):
        return None


def to_quarterly_performance(periods):
    by_period = {}
    for period in periods:
        report_date = period.get("reportDate")
        quarter = quarter_number(report_date)
        if report_date and quarter:
            by_period[(str(report_date)[:4], quarter)] = period
    result = []
    for period in periods:
        report_date = period.get("reportDate")
        quarter = quarter_number(report_date)
        if not report_date or not quarter:
            continue
        year = str(report_date)[:4]
        previous = by_period.get((year, quarter - 1)) if quarter > 1 else None

        def single_quarter(field):
            current_value = period.get(field)
            if current_value is None:
                return None
            previous_value = previous.get(field) if previous else None
            return current_value - previous_value if quarter > 1 and previous_value is not None else current_value

        result.append({
            "period": f"{year} Q{quarter}",
            "reportDate": report_date,
            "revenue": single_quarter("revenue"),
            "netProfit": single_quarter("netProfit"),
            "source": period.get("source"),
        })
    return result[:8]


def load_leader_fundamentals(code, full_code):
    cached = LEADER_FUNDAMENTALS_CACHE.get(full_code)
    if cached and time.time() - cached[0] <= 1800:
        return cached[1]
    executor = concurrent.futures.ThreadPoolExecutor(max_workers=2)
    performance_future = executor.submit(load_performance, code, full_code)
    report_future = executor.submit(load_reports, code)
    try:
        performance_result = performance_future.result(timeout=REQUEST_TIMEOUT * 2)
    except Exception:
        performance_result = {"performance": []}
    try:
        report_result = report_future.result(timeout=REQUEST_TIMEOUT * 2)
    except Exception:
        report_result = {"researchReports": []}
    executor.shutdown(wait=False, cancel_futures=True)
    reports = report_result.get("researchReports", [])
    payload = {
        "quarterlyPerformance": to_quarterly_performance(
            performance_result.get("performance", [])),
        "latestReport": reports[0] if reports else None,
    }
    LEADER_FUNDAMENTALS_CACHE[full_code] = (time.time(), payload)
    return payload


def cached_leader_fundamentals(full_code):
    cached = LEADER_FUNDAMENTALS_CACHE.get(full_code)
    if cached and time.time() - cached[0] <= 1800:
        return cached[1]
    return {"quarterlyPerformance": [], "latestReport": None}


def build_leader_reason(row, sector_name, selected):
    classification_source = text(row_value(row, "_classificationSource")) or "当前数据源"
    source_label = "同花顺" if classification_source == "同花顺" else classification_source
    reasons = [f"{sector_name}{source_label}正式成分股"]
    if number(row_value(row, "_scale")) >= 80:
        reasons.append("板块市值前20%")
    if number(row_value(row, "_liquidity")) >= 80:
        reasons.append("板块成交额前20%")
    if number(row_value(row, "_momentum")) >= 80:
        reasons.append("日内涨幅位于板块前20%")
    if number(row_value(row, "_turnover")) >= 80:
        reasons.append("换手活跃度位于板块前20%")
    pe = number(row_value(row, "per"))
    if pe is not None and pe > 0:
        reasons.append("动态PE为正，具备盈利估值基础")
    if selected and len(reasons) == 1:
        reasons.append("所选个股纳入板块强度对照")
    if len(reasons) == 1:
        reasons.append("综合市值、成交与日内强度排名靠前")
    return "；".join(reasons[:4])


def analyze_member(row, selected_code, sector_name, realtime_flow=None,
                   limit_status=None, fast_refresh=False):
    full_code = text(row_value(row, "symbol"))
    limit_status = limit_status or {}
    result = {
        "fullCode": full_code,
        "code": text(row_value(row, "code")),
        "name": text(row_value(row, "name")),
        "currentPrice": number(row_value(row, "trade")),
        "dailyChangePercent": number(row_value(row, "changepercent")),
        "amount": number(row_value(row, "amount")),
        "turnoverRate": number(row_value(row, "turnoverratio")),
        "pe": number(row_value(row, "per")),
        "pb": number(row_value(row, "pb")),
        "marketValue": number(row_value(row, "mktcap")),
        "selected": bool(selected_code and full_code.lower() == selected_code.lower()),
        "limitUp": bool(limit_status.get("limitUp")),
        "sealAmount": limit_status.get("sealAmount"),
        "breakoutCount": limit_status.get("breakoutCount"),
        "continuousBoards": limit_status.get("continuousBoards"),
        "sealRatio": None,
        "limitQualityScore": None,
        "warning": None,
    }
    if result["limitUp"]:
        amount = result.get("amount") or 0
        seal_amount = result.get("sealAmount")
        seal_ratio = seal_amount / amount if seal_amount is not None and amount > 0 else None
        result["sealRatio"] = seal_ratio
        seal_score = clamp(50 + (seal_ratio or 0) * 500)
        breakout_count = result.get("breakoutCount")
        stability_score = 100 if breakout_count == 0 else 82 if breakout_count == 1 else 62 if breakout_count == 2 else 40
        boards = result.get("continuousBoards") or 0
        continuity_score = clamp(50 + boards * 12)
        result["limitQualityScore"] = round(
            seal_score * 0.50 + stability_score * 0.35 + continuity_score * 0.15, 1
        )
    result["leaderReason"] = build_leader_reason(row, sector_name, result["selected"])
    pe = result["pe"]
    result["performanceLabel"] = (
        "盈利估值可用" if pe is not None and 0 < pe <= 120
        else "高估值盈利股" if pe is not None and pe > 120
        else "亏损或暂无PE"
    )
    try:
        minute_metrics = load_minute_metrics(
            full_code,
            result.get("currentPrice"),
            text(row_value(row, "_quoteTime")),
        )
        result.update(minute_metrics)
        if result.get("currentPrice") is None and minute_metrics.get("points"):
            result["currentPrice"] = minute_metrics["points"][-1].get("price") or result["currentPrice"]
    except Exception as exc:
        result["warning"] = f"分钟行情不可用: {exc}"
        result.update({
            "return1m": None, "return3m": None, "return5m": None,
            "amplitude1m": None, "amplitude3m": None, "amplitude5m": None,
            "volumeRatio": None, "volumeExpanded": False, "points": [], "minuteDataTime": None,
        })
    minute_date = text(result.get("minuteDataTime"))
    flow_date = text((realtime_flow or {}).get("flowDate"))
    flow_is_current = bool(
        minute_date and flow_date and minute_date[:10] == flow_date[:10]
    )
    result.update(realtime_flow if flow_is_current else {
        "mainNetInflow": None, "mainNetRatio": None, "flowDate": None,
    })
    if fast_refresh:
        result.update(cached_leader_fundamentals(full_code))
    else:
        try:
            result.update(load_leader_fundamentals(result["code"], full_code))
        except Exception:
            result.update({"quarterlyPerformance": [], "latestReport": None})
    return result


def load_sector_detail(sector_identifier, selected_code=None, refresh=False):
    sector = resolve_sector(sector_identifier)
    if not sector:
        return {
            "available": False,
            "sector": None,
            "selectedCode": selected_code,
            "stocks": [],
            "formula": None,
            "source": "AKShare板块服务",
            "warnings": ["未找到板块: " + str(sector_identifier)],
            "fetchedAt": dt.datetime.now().isoformat(timespec="seconds"),
        }
    cache_key = f"sector-detail:{sector['id']}:{selected_code or ''}"
    if not refresh:
        cached = cache_get(cache_key, 20)
        if cached:
            return cached
    try:
        members = load_sector_members(sector["id"], refresh=refresh)
    except Exception as exc:
        # Upstream board pages occasionally return an empty body. Returning a
        # normal response keeps scheduled multi-sector refreshes alive and
        # lets the Java layer show a warning instead of logging HTTP 500.
        last_good = SECTOR_DETAIL_LAST_GOOD.get(cache_key)
        warning = f"板块成分本次刷新暂不可用: {exc}"
        if last_good:
            warning += "；已保留上一次成功结果"
        return {
            "available": False,
            "sector": sector,
            "selectedCode": selected_code,
            "stocks": [],
            "formula": last_good.get("formula") if last_good else None,
            "source": last_good.get("source") if last_good else "AKShare板块服务",
            "warnings": [warning],
            "fetchedAt": dt.datetime.now().isoformat(timespec="seconds"),
        }
    sector = dict(sector)
    if not sector.get("companyCount"):
        sector["companyCount"] = int(len(members))
    members = members.copy()
    if "_classificationSource" not in members.columns:
        members["_classificationSource"] = sector.get("classificationSource", "当前数据源")
    if (str(sector["id"]).startswith("ths_industry_local_")
            and sector["id"] not in THS_LEADER_CACHE):
        members = overlay_realtime_quotes(members)
    selected = load_or_create_local_leaders(sector["id"], members)
    selected = overlay_realtime_quotes(selected)
    selected_codes = [
        text(row_value(row, "symbol"))
        for _, row in selected.iterrows()
        if text(row_value(row, "symbol"))
    ]
    market_executor = concurrent.futures.ThreadPoolExecutor(max_workers=2)
    limit_future = market_executor.submit(load_limit_up_status, selected_codes)
    flow_future = market_executor.submit(
        load_realtime_stock_flows, selected_codes, sector["id"])
    try:
        limit_statuses = limit_future.result(timeout=REQUEST_TIMEOUT + 1)
    except Exception:
        limit_statuses = {}
    try:
        realtime_flows = flow_future.result(timeout=REQUEST_TIMEOUT + 1)
    except Exception:
        realtime_flows = {}
    market_executor.shutdown(wait=False, cancel_futures=True)
    executor = concurrent.futures.ThreadPoolExecutor(max_workers=min(10, max(1, len(selected))))
    futures = [
        executor.submit(
            analyze_member,
            row,
            selected_code,
            sector["name"],
            realtime_flows.get((text(row_value(row, "symbol")) or "").lower()),
            limit_statuses.get((text(row_value(row, "symbol")) or "").lower()),
            refresh,
        )
        for _, row in selected.iterrows()
    ]
    stocks = []
    for future in futures:
        try:
            stocks.append(future.result(timeout=REQUEST_TIMEOUT * 3))
        except Exception:
            pass
    executor.shutdown(wait=False, cancel_futures=True)
    available_returns = [item["return5m"] for item in stocks if item.get("return5m") is not None]
    sector_return = sum(available_returns) / len(available_returns) if available_returns else 0
    for item in stocks:
        item["relativeStrength"] = (
            item["return5m"] - sector_return if item.get("return5m") is not None else None
        )
        score = (
            component_score(item.get("return5m"), 8) * 0.25
            + component_score(item.get("return3m"), 10) * 0.20
            + component_score(item.get("return1m"), 14) * 0.10
            + component_score(item.get("relativeStrength"), 10) * 0.15
            + component_score((item.get("volumeRatio") - 1) if item.get("volumeRatio") is not None else None, 35) * 0.15
            + component_score(item.get("mainNetRatio"), 3) * 0.15
        )
        if item.get("limitUp"):
            limit_quality = item.get("limitQualityScore") or 50
            # 涨停时分数据通常为空或为 0，封板质量应成为主要评分依据。
            score = limit_up_strength_score(limit_quality, item.get("breakoutCount"))
            if item.get("breakoutCount") == 0:
                score += 8
        if (item.get("return5m") or 0) < 0 and (item.get("mainNetInflow") or 0) < 0:
            score -= 10
        item["score"] = round(clamp(score), 1)
        signals = []
        if item.get("volumeExpanded"):
            signals.append("放量上攻" if (item.get("return5m") or 0) > 0 else "放量回落")
        if item.get("mainNetInflow") is not None:
            signals.append("资金净流入" if item["mainNetInflow"] > 0 else "资金净流出")
        if item.get("limitUp"):
            signals.append("涨停封板")
            if item.get("breakoutCount") == 0:
                signals.append("零炸板")
            elif item.get("breakoutCount") is not None:
                signals.append(f"炸板{item['breakoutCount']}次")
            if item.get("sealRatio") is not None and item["sealRatio"] >= 0.05:
                signals.append("封单较强")
        item["signals"] = signals
    stocks.sort(key=lambda item: item["score"], reverse=True)
    daily_changes = [item.get("dailyChangePercent") for item in stocks
                     if item.get("dailyChangePercent") is not None]
    daily_leader_change = max(daily_changes) if daily_changes else None
    for rank, item in enumerate(stocks, start=1):
        item["rank"] = rank
        item["strengthLabel"] = (
            "板块内分时最强" if rank == 1 and item["score"] >= 65
            else "板块内强势" if rank <= 3 and item["score"] >= 55
            else "板块内偏弱" if item["score"] < 45
            else "板块内中性"
        )
        item["rankingReason"] = build_ranking_reason(item, rank, daily_leader_change)
    warnings = []
    missing_minutes = sum(not item.get("points") for item in stocks)
    missing_flow = sum(item.get("mainNetInflow") is None for item in stocks)
    missing_performance = sum(not item.get("quarterlyPerformance") for item in stocks)
    missing_reports = sum(item.get("latestReport") is None for item in stocks)
    if missing_minutes:
        warnings.append(f"{missing_minutes} 只候选股缺少分钟行情")
    if missing_flow:
        warnings.append(f"{missing_flow} 只候选股今日实时资金流暂不可用")
    if missing_performance:
        warnings.append(f"{missing_performance} 只候选股近两年季度业绩暂不可用")
    if missing_reports:
        warnings.append(f"{missing_reports} 只候选股近期研报评级暂不可用")
    payload = {
        "available": bool(stocks),
        "sector": sector,
        "selectedCode": selected_code,
        "stocks": stocks,
        "formula": "Top10先按板块成分相关性、成交额、市值、动态PE盈利状态、日内涨幅和换手率筛选；普通股分时强度分=5分钟动量25%+3分钟动量20%+1分钟动量10%+板块相对强度15%+5分钟量比15%+今日实时主力净流入占比15%；涨停股改用基础分55%+封板质量45%，零炸板额外加分。",
        "source": f"{sector.get('classificationSource', 'AKShare / 新浪')}板块成分、分钟行情与今日实时资金流；同花顺季度业绩，研报数据按当前可用接口返回",
        "warnings": warnings,
        "fetchedAt": dt.datetime.now().isoformat(timespec="seconds"),
    }
    SECTOR_DETAIL_LAST_GOOD[cache_key] = payload
    return cache_put(cache_key, payload)


def load_stock_sector_detail(full_code, refresh=False):
    sector = resolve_stock_sector(full_code, refresh=refresh)
    if not sector:
        return {
            "available": False,
            "sector": None,
            "selectedCode": full_code,
            "stocks": [],
            "formula": None,
            "source": "同花顺行业分类（AKShare可用时回退）",
            "warnings": ["暂时无法识别该股票所属的行业板块"],
            "fetchedAt": dt.datetime.now().isoformat(timespec="seconds"),
        }
    return load_sector_detail(sector["id"], selected_code=full_code, refresh=refresh)


def load_stock_sector_mapping(full_code):
    """Resolve only the canonical local sector; used by keyword search."""
    sector = resolve_stock_sector(full_code, refresh=False)
    return {
        "available": bool(sector),
        "sector": sector,
        "selectedCode": full_code,
        "source": "同花顺三级行业本地映射",
        "fetchedAt": dt.datetime.now().isoformat(timespec="seconds"),
    }


SERVICE_PROTOCOL = "stock-lens-akshare-v2"
INSTANCE_TOKEN = ""


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        parsed = urlparse(self.path)
        path = unquote(parsed.path)
        query = parse_qs(parsed.query)
        refresh = query.get("refresh", ["false"])[0].lower() == "true"
        if path == "/health":
            self.respond(200, {
                "status": "ok",
                "provider": "akshare",
                "protocol": SERVICE_PROTOCOL,
                "instanceToken": INSTANCE_TOKEN,
            })
            return
        try:
            if path == "/api/sectors":
                self.respond(200, load_sector_list(refresh=refresh))
                return
            if path == "/api/sectors/search":
                keyword = query.get("keyword", [""])[0]
                self.respond(200, search_sectors(keyword, refresh=refresh))
                return
            stock_sector_match = re.fullmatch(
                r"/api/sectors/stock/((?:sh|sz)\d{6})", path, re.IGNORECASE)
            if stock_sector_match:
                self.respond(200, load_stock_sector_detail(
                    stock_sector_match.group(1).lower(), refresh=refresh))
                return
            stock_sector_map_match = re.fullmatch(
                r"/api/sectors/stock-map/((?:sh|sz)\d{6})", path, re.IGNORECASE)
            if stock_sector_map_match:
                self.respond(200, load_stock_sector_mapping(
                    stock_sector_map_match.group(1).lower()))
                return
            sector_match = re.fullmatch(r"/api/sectors/(.+)", path, re.IGNORECASE)
            if sector_match:
                selected = query.get("selected", [None])[0]
                self.respond(200, load_sector_detail(
                    sector_match.group(1), selected_code=selected, refresh=refresh))
                return
            stock_match = re.fullmatch(r"/api/stock/((?:sh|sz)\d{6})", path, re.IGNORECASE)
            if stock_match:
                self.respond(200, build_snapshot(stock_match.group(1).lower()))
                return
            self.respond(404, {"message": "not found"})
        except Exception as exc:
            self.respond(500, {"message": str(exc)})

    def respond(self, status, payload):
        body = json.dumps(
            payload,
            ensure_ascii=False,
            allow_nan=False,
            default=clean,
        ).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, fmt, *args):
        print("%s - %s" % (self.address_string(), fmt % args), flush=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--request-timeout", type=int, default=7)
    parser.add_argument("--instance-token", default="")
    args = parser.parse_args()
    global REQUEST_TIMEOUT, INSTANCE_TOKEN
    REQUEST_TIMEOUT = max(3, args.request_timeout)
    INSTANCE_TOKEN = args.instance_token
    socket.setdefaulttimeout(REQUEST_TIMEOUT)
    server = ThreadingHTTPServer((args.host, args.port), Handler)
    print(f"AKShare service listening on http://{args.host}:{args.port}", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
