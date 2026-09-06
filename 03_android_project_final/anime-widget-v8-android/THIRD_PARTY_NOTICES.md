# Third-party notices

## llama.cpp

The Android ARM64 inference library in this application is built from
`ggml-org/llama.cpp` v0.3.0, commit
`c1d0e7a004015f23bc0233470b747b596f29b264`.

Upstream source: https://github.com/ggml-org/llama.cpp

Local integration changes use a generic ARM64 CPU build, reduce the one-turn
context to 2048 tokens, avoid logging prompt contents, fix the sample generation
limit calculation, and make cleanup safe after a partial initialization failure.

llama.cpp is licensed under the MIT License:

Copyright (c) 2023-2026 The ggml authors

Permission is hereby granted, free of charge, to any person obtaining a copy of
this software and associated documentation files (the "Software"), to deal in
the Software without restriction, including without limitation the rights to
use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
the Software, and to permit persons to whom the Software is furnished to do so,
subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

## Optional model

The model is not bundled in the APK. It is downloaded only after an explicit
user action from:

- Repository: https://modelscope.cn/models/unsloth/Qwen3.5-0.8B-GGUF
- File: `Qwen3.5-0.8B-Q4_K_M.gguf`
- Expected size: `532517120` bytes
- Expected SHA-256:
  `bd258782e35f7f458f8aced1adc053e6e92e89bc735ba3be89d38a06121dc517`

The model repository's own license and usage terms apply to the downloaded
model.
