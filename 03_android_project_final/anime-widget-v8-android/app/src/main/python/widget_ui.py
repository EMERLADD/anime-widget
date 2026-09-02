from widget_theme import theme_color


def rounded(draw, box, r=24, fill=None, outline=None, width=2):
    if fill is None:
        fill = theme_color("pill_background", (25, 90, 170, 145))
    if outline is None:
        outline = theme_color("border", (225, 245, 255, 120))
    draw.rounded_rectangle(box, radius=r, fill=fill, outline=outline, width=width)

def centered_text(draw, box, text, fnt, fill="white", stroke_width=0, stroke_fill=None):
    x1,y1,x2,y2=box
    bb=draw.textbbox((0,0),text,font=fnt,stroke_width=stroke_width)
    tw=bb[2]-bb[0]; th=bb[3]-bb[1]
    x=x1+(x2-x1-tw)//2
    y=y1+(y2-y1-th)//2-bb[1]
    kw={"font":fnt,"fill":fill}
    if stroke_width:
        kw["stroke_width"]=stroke_width
        kw["stroke_fill"]=stroke_fill
    draw.text((x,y),text,**kw)

def wrap_text(draw, s, fnt, max_width, max_lines=2):
    manual = s.split("\\n")
    if len(manual) > 1:
        return manual[:max_lines]
    lines=[]; cur=""
    for ch in s:
        test=cur+ch
        bb=draw.textbbox((0,0),test,font=fnt)
        if bb[2]-bb[0] <= max_width:
            cur=test
        else:
            if cur:
                lines.append(cur)
            cur=ch
    if cur:
        lines.append(cur)
    return lines[:max_lines]
