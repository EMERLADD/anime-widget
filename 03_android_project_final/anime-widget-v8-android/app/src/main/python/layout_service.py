"""布局 JSON 读写服务。

只依赖 widget_config 中的 DEFAULT_LAYOUT / LAYOUT_FILE / load_layout,
提供完整布局的读取与保存,以及局部位置更新的合并。
"""
import json
import os
import tempfile

from widget_config import DEFAULT_LAYOUT, LAYOUT_FILE, load_layout
from layout_geometry import MODULE_NAMES, get_position, move_module, module_geometry_dict


def _dump(layout):
    return json.dumps(layout, ensure_ascii=False, indent=2)


def _parse_top_dict(text):
    """解析 JSON 并确保顶层是 dict,否则抛 ValueError。"""
    try:
        data = json.loads(str(text))
    except json.JSONDecodeError as exc:
        raise ValueError(f"不是合法 JSON: {exc}") from exc
    if not isinstance(data, dict):
        raise ValueError("顶层必须是 JSON 对象(dict)")
    return data


def get_layout_json():
    """返回当前完整布局的 ensure_ascii=False JSON 字符串。"""
    layout = load_layout()
    for name in MODULE_NAMES:
        x, y = get_position(layout, name)
        layout = move_module(layout, name, x, y)
    return _dump(layout)


def get_layout_geometry_json():
    """Return shared scale=100 frame/visual bounds for the layout editor."""
    return _dump(module_geometry_dict(load_layout()))


def save_layout_json(text):
    """保存布局并返回保存后的 JSON 字符串。

    - 只接受 DEFAULT_LAYOUT 已知模块及已知字段,未知键忽略;
    - 每个值尝试转 int,失败时回退为当前 load_layout() 的对应值;
    - 写入完整规范化布局(基于 load_layout(),不丢失未提交模块);
    - 使用 LAYOUT_FILE 同目录临时文件 + replace,避免半写入。
    """
    data = _parse_top_dict(text)
    current = load_layout()
    for name, fields in data.items():
        if name not in DEFAULT_LAYOUT or not isinstance(fields, dict):
            continue  # 未知模块忽略
        for key, value in fields.items():
            if key not in DEFAULT_LAYOUT[name]:
                continue  # 未知字段忽略
            try:
                current[name][key] = int(value)
            except (TypeError, ValueError):
                pass  # 转换失败,保留 load_layout() 的当前值
    for name in MODULE_NAMES:
        x, y = get_position(current, name)
        current = move_module(current, name, x, y)
    payload = _dump(current)

    LAYOUT_FILE.parent.mkdir(parents=True, exist_ok=True)
    fd, tmp_path = tempfile.mkstemp(
        dir=str(LAYOUT_FILE.parent), prefix="layout-", suffix=".tmp"
    )
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as f:
            f.write(payload)
        os.replace(tmp_path, str(LAYOUT_FILE))
    except BaseException:
        try:
            os.unlink(tmp_path)
        except OSError:
            pass
        raise
    return payload


def update_positions_json(text):
    """接受局部 JSON(如 {"clock":{"x":100,"y":150}}),
    与当前布局合并后复用 save_layout_json,返回完整 JSON。"""
    data = _parse_top_dict(text)
    current = load_layout()
    for name, fields in data.items():
        if name not in MODULE_NAMES or not isinstance(fields, dict):
            continue  # 未知模块忽略
        try:
            old_x, old_y = get_position(current, name)
            x = int(fields.get("x", old_x))
            y = int(fields.get("y", old_y))
        except (TypeError, ValueError):
            continue
        for key in ("scale", "visible"):
            if key in fields and key in DEFAULT_LAYOUT[name]:
                try:
                    current[name][key] = int(fields[key])
                except (TypeError, ValueError):
                    pass
        current = move_module(current, name, x, y)
    return save_layout_json(_dump(current))
    
    
def reset_layout_json():
    return save_layout_json(_dump(DEFAULT_LAYOUT))
