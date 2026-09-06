"""Draw the translucent Widget affordance which launches the native question UI."""
from PIL import Image, ImageDraw

from widget_theme import theme_color
from widget_ui import centered_text

WIDTH = 430
HEIGHT = 72


def draw_ai_input(image, font, config):
    x, y = config["x"], config["y"]
    base_fill = theme_color("bubble_fill", (250, 253, 255, 236))
    base_outline = theme_color("bubble_outline", (116, 157, 214, 225))
    text_color = theme_color("bubble_text", (27, 55, 119, 255))
    fill = (*base_fill[:3], 165)
    outline = (*base_outline[:3], 220)

    layer = Image.new("RGBA", image.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    draw.rounded_rectangle(
        (x, y, x + WIDTH, y + HEIGHT),
        radius=HEIGHT // 2,
        fill=fill,
        outline=outline,
        width=3,
    )
    centered_text(
        draw,
        (x + 24, y, x + WIDTH - 42, y + HEIGHT),
        "点击以询问天气意见",
        font,
        text_color,
    )
    draw.polygon(
        ((x + WIDTH - 35, y + 25), (x + WIDTH - 19, y + 36), (x + WIDTH - 35, y + 47)),
        fill=text_color,
    )
    image.alpha_composite(layer)
