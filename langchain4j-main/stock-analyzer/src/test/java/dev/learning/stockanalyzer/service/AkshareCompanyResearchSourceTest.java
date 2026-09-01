package dev.learning.stockanalyzer.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AkshareCompanyResearchSourceTest {

    @Test
    void companyResearchShouldUseThsAndKeepOriginalResearchReportProvider() throws Exception {
        String script = Files.readString(
                Path.of("scripts", "akshare_service.py"), StandardCharsets.UTF_8);

        assertThat(script)
                .contains(
                        "stock_zyjs_ths",
                        "stock_financial_abstract_new_ths",
                        "stock_research_report_em",
                        "stock-lens-akshare-v2",
                        "instanceToken",
                        "--instance-token")
                .doesNotContain(
                        "stock_individual_info_em",
                        "stock_financial_analysis_indicator_em",
                        "stock_value_em",
                        "stock_zh_valuation_comparison_em",
                        "stock_zh_growth_comparison_em");
    }
}
