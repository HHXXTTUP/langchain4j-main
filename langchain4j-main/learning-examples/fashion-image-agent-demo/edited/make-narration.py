import asyncio
from pathlib import Path
import edge_tts

BASE = Path(r"E:\ai-workspace\langchain4j-main\langchain4j-main\learning-examples\fashion-image-agent-demo\edited")
TEXT = (BASE / "narration.txt").read_text(encoding="utf-8")

async def main():
    communicate = edge_tts.Communicate(TEXT, "zh-CN-YunxiNeural", rate="+0%", volume="+0%")
    await communicate.save(str(BASE / "narration.mp3"))

asyncio.run(main())
