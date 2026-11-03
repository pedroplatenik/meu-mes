#!/bin/bash
# Compila o Meu Mês para Windows (WinForms + WebView2) com o compilador mcs do Mono.
# As DLLs do WebView2 (Microsoft) não ficam no repositório: o script baixa o pacote
# pywebview pelo pip, que traz as DLLs redistribuíveis, e copia para build/.
set -euo pipefail
cd "$(dirname "$0")"
rm -rf build tmp && mkdir -p build tmp
pip download pywebview --no-deps -q -d tmp
(cd tmp && unzip -q -o pywebview-*.whl 'webview/lib/*')
cp tmp/webview/lib/Microsoft.Web.WebView2.Core.dll tmp/webview/lib/Microsoft.Web.WebView2.WinForms.dll build/
cp -r tmp/webview/lib/runtimes build/
cp MeuMes.exe.config build/
mcs -target:winexe -sdk:4.7.2 -optimize+ -codepage:utf8 -out:build/MeuMes.exe -win32icon:app.ico \
  -r:System.Windows.Forms.dll -r:System.Drawing.dll \
  -r:build/Microsoft.Web.WebView2.Core.dll -r:build/Microsoft.Web.WebView2.WinForms.dll \
  -resource:index.html,MeuMes.index.html MeuMes.cs
rm -rf tmp
echo "OK: build/MeuMes.exe (leve a pasta build inteira)"
