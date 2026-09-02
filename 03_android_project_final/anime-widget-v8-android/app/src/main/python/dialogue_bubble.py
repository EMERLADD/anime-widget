"""Draw dialogue as an oval layer independent from character artwork."""
from PIL import Image, ImageDraw, ImageFilter
from widget_ui import wrap_text
from widget_theme import theme_color


def draw_dialogue_bubble(image, text, font, config):
    x1, y1, x2, y2 = (config[k] for k in ("x1", "y1", "x2", "y2"))
    tail_x, tail_y = config["tail_x"], config["tail_y"]
    padding = config["padding"]

    shadow = Image.new("RGBA", image.size, (0, 0, 0, 0))
    sd = ImageDraw.Draw(shadow)
    shadow_color = theme_color("bubble_shadow", (24, 58, 110, 65))
    sd.ellipse((x1 + 7, y1 + 9, x2 + 7, y2 + 9), fill=shadow_color)
    sd.ellipse((tail_x - 22, tail_y - 17, tail_x + 22, tail_y + 27), fill=shadow_color)
    sd.ellipse((tail_x + 35, tail_y + 28, tail_x + 58, tail_y + 51), fill=shadow_color)
    image.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(9)))

    layer = Image.new("RGBA", image.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    fill = theme_color("bubble_fill", (250, 253, 255, 236))
    outline = theme_color("bubble_outline", (116, 157, 214, 225))
    draw.ellipse((x1, y1, x2, y2), fill=fill, outline=outline, width=3)
    draw.ellipse((tail_x - 22, tail_y - 22, tail_x + 22, tail_y + 22), fill=fill, outline=outline, width=3)
    draw.ellipse((tail_x + 38, tail_y + 27, tail_x + 60, tail_y + 49), fill=fill, outline=outline, width=2)

    # Ellipses narrow near their edges, so reserve more horizontal padding than
    # a rounded rectangle and keep the text centered in the broad middle band.
    lines = wrap_text(draw, text, font, x2 - x1 - padding * 3, 3)
    gap = 9
    boxes = [draw.textbbox((0, 0), line, font=font) for line in lines]
    heights = [box[3] - box[1] for box in boxes]
    y = y1 + (y2 - y1 - sum(heights) - gap * max(0, len(lines) - 1)) // 2
    for line, box, line_h in zip(lines, boxes, heights):
        x = x1 + (x2 - x1 - (box[2] - box[0])) // 2
        draw.text((x, y - box[1]), line, font=font, fill=theme_color("bubble_text", (27, 55, 119, 255)))
        y += line_h + gap
    image.alpha_composite(layer)
