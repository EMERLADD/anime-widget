#!/data/data/com.termux/files/usr/bin/bash

cd "$HOME/anime-widget" || exit 1

python render.py
python widget.py
