#!/usr/bin/env bash
# Builds the Channels app (module `channels` of majnioui/CleanXiaomiTV, MIT) with OUR list of channels:
#   channels/channels.m3u        -> its res/raw/channels.m3u (the 311 channels)
#   channels/ChannelList.java    -> the app drops an old saved list (a build with other channels) instead of showing it
# Result: channels/out/channels-release.apk, signed with the debug key like the other ChouiTV apps (installs over the old one).
#   channels/build-channels.sh              (needs JDK 17, ANDROID_HOME with platform 34 + build-tools, gradle 8.x)
# Bump CH_VERSION_CODE / CH_VERSION_NAME when the list or the code changes.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
UPSTREAM_COMMIT=f12af15
CH_VERSION_CODE="${CH_VERSION_CODE:-3}"
CH_VERSION_NAME="${CH_VERSION_NAME:-1.2-chouitv}"
GRADLE="${GRADLE:-gradle}"
W="$(mktemp -d)"; trap 'rm -rf "$W"' EXIT
curl -fsSL "https://codeload.github.com/majnioui/CleanXiaomiTV/tar.gz/$UPSTREAM_COMMIT" | tar -xz -C "$W" --strip-components=1
M="$W/modules"
cp "$HERE/channels.m3u" "$M/channels/src/main/res/raw/channels.m3u"
cp "$HERE/ChannelList.java" "$M/channels/src/main/java/com/majnioui/tv/channels/ChannelList.java"
sed -i "s/versionCode 1$/versionCode $CH_VERSION_CODE/; s/versionName '1.0'/versionName '$CH_VERSION_NAME'/" "$M/channels/build.gradle"
echo "sdk.dir=${ANDROID_HOME:?set ANDROID_HOME}" > "$M/local.properties"
(cd "$M" && "$GRADLE" :channels:assembleRelease --no-daemon -q)
mkdir -p "$HERE/out"
cp "$M/channels/build/outputs/apk/release/channels-release.apk" "$HERE/out/channels-release.apk"
echo "built $HERE/out/channels-release.apk ($(grep -c '^#EXTINF' "$HERE/channels.m3u") channels, $CH_VERSION_NAME)"
