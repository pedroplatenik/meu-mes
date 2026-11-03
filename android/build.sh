#!/bin/bash
# Gera o APK do Meu Mês sem Gradle: aapt -> javac -> dx -> aapt -> zipalign -> apksigner
# Precisa do Android SDK (platform android-23 e build-tools 29) e do JDK.
# A chave de assinatura não fica no repositório: na primeira vez o script cria uma nova.
#   MEUMES_SENHA_CHAVE  senha da chave (obrigatória)
#   ANDROID_SDK         pasta do SDK (padrão /usr/lib/android-sdk)
set -euo pipefail
cd "$(dirname "$0")"
: "${MEUMES_SENHA_CHAVE:?Defina MEUMES_SENHA_CHAVE com a senha da chave de assinatura}"
SDK=${ANDROID_SDK:-/usr/lib/android-sdk}
JAR=$SDK/platforms/android-23/android.jar
BT=$SDK/build-tools/29.0.3
OUT=build
rm -rf $OUT && mkdir -p $OUT/gen $OUT/obj

aapt package -f -m -J $OUT/gen -M AndroidManifest.xml -S res -I $JAR
javac -Xlint:-options -source 8 -target 8 -encoding UTF-8 -bootclasspath $JAR -classpath $JAR -d $OUT/obj \
  $(find src -name '*.java') $(find $OUT/gen -name '*.java')
$BT/dx --dex --min-sdk-version=24 --output=$OUT/classes.dex $OUT/obj
aapt package -f -M AndroidManifest.xml -S res -A assets -I $JAR -F $OUT/sem-assinatura.apk
(cd $OUT && aapt add sem-assinatura.apk classes.dex > /dev/null)
zipalign -f -p 4 $OUT/sem-assinatura.apk $OUT/alinhado.apk

KS=chave/meumes.jks
if [ ! -f $KS ]; then
  mkdir -p chave
  keytool -genkeypair -keystore $KS -storepass "$MEUMES_SENHA_CHAVE" -keypass "$MEUMES_SENHA_CHAVE" -alias meumes -keyalg RSA -keysize 2048 \
    -validity 20000 -dname "CN=Meu Mes, O=Pedro Platenik, C=BR" > /dev/null
fi
apksigner sign --ks $KS --ks-pass env:MEUMES_SENHA_CHAVE --key-pass env:MEUMES_SENHA_CHAVE --ks-key-alias meumes --out $OUT/MeuMes.apk $OUT/alinhado.apk
apksigner verify $OUT/MeuMes.apk
echo "OK: $OUT/MeuMes.apk ($(du -h $OUT/MeuMes.apk | cut -f1))"
