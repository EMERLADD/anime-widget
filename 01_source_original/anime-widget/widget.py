import termuxgui as tg
from pathlib import Path

WIDGET_ID = "712815c4-4107-4645-bc8b-926e4adbd88d"
IMAGE = Path.home() / "anime-widget/cache/widget.png"

data = IMAGE.read_bytes()

print(f"PNG size: {len(data)} bytes")

with tg.Connection() as c:
    rv = tg.RemoteViews(c)

    image = rv.addImageView()
    rv.setImage(image, data)

    rv.updateWidget(WIDGET_ID)

print("Widget updated.")
