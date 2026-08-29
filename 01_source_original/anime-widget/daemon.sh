#!/data/data/com.termux/files/usr/bin/bash

BASE="$HOME/anime-widget"
cd "$BASE" || exit 1

DATA_INTERVAL=1800
last_data=0

echo "Anime Widget daemon started."

# 立即先渲染一次，让小组件马上出现（而不是干等最多 60 秒）。
now=$(date +%s)
if [ $((now - last_data)) -ge "$DATA_INTERVAL" ]; then
    if python "$BASE/update_data.py"; then
        last_data=$now
    else
        echo "Data update failed, keeping old cache."
    fi
fi
python "$BASE/render.py"
python "$BASE/widget.py"

# 之后每次对齐到整分钟（:00）再刷新。
# 睡到下一个 :00，而不是固定 sleep 60——这样能消除
# 因为"脚本执行耗时 > 60 秒"造成的累积漂移，时钟也能在
# 每分钟开头准时翻页，不再晚十几秒。
while true; do
    sleep $(( (60 - $(date +%s) % 60) % 60 ))

    now=$(date +%s)
    if [ $((now - last_data)) -ge "$DATA_INTERVAL" ]; then
        if python "$BASE/update_data.py"; then
            last_data=$now
        else
            echo "Data update failed, keeping old cache."
        fi
    fi

    python "$BASE/render.py"
    python "$BASE/widget.py"
done
