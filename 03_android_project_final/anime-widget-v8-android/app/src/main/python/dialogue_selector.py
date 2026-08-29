"""Select built-in, custom, or mixed dialogue."""
import random
from dialogue import get_builtin_line
from dialogue_customize import get_custom_line, load_config


def get_maid_line(weather, now, folder, variation=0):
    config = load_config(); mode = config["mode"]
    if mode == "builtin": return get_builtin_line(weather, now, folder, variation)
    custom = get_custom_line(weather, now, variation, config)
    if mode == "custom": return custom or get_builtin_line(weather, now, folder, variation)
    choose_custom = random.Random(f"dialogue-source-{now:%Y%m%d%H}-{now.minute // 30}-{variation}").randrange(100) < config["custom_weight"]
    return custom if choose_custom and custom else get_builtin_line(weather, now, folder, variation)
