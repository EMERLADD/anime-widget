#!/bin/sh
F="$HOME/Python-3.13.7/Modules/pwdmodule.c"

# 在 #include <pwd.h> 后插入 Android 平台的 stub 声明
sed -i '/#include <pwd.h>/a\
#ifdef __ANDROID__\
static inline void setpwent(void) {}\
static inline struct passwd *getpwent(void) { return NULL; }\
static inline void endpwent(void) {}\
#endif' "$F"

echo "PATCHED"
grep -n -A4 '#include <pwd.h>' "$F" | head -8
