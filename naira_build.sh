#!/data/data/com.termux/files/usr/bin/bash
set -e
cd "$HOME/ZehnFull"

if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    git init
    git branch -M main
fi

REMOTE=$(git remote get-url origin 2>/dev/null || true)

if [ -z "$REMOTE" ]; then
    echo "GitHub Repository HTTPS link paste karo:"
    read -r REMOTE
    git remote add origin "$REMOTE"
fi

if [[ "$REMOTE" == git@github.com:* ]]; then
    REMOTE="https://github.com/${REMOTE#git@github.com:}"
    git remote set-url origin "$REMOTE"
fi

REMOTE=${REMOTE%.git}

if [[ "$REMOTE" =~ github.com/([^/]+)/([^/]+)$ ]]; then
    OWNER="${BASH_REMATCH[1]}"
    NAME="${BASH_REMATCH[2]}"
else
    echo "GitHub link galat hai."
    exit 1
fi

mkdir -p .github/workflows

cat > .github/workflows/android-apk.yml <<'YAML'
name: Naira AI APK Builder

on:
  workflow_dispatch:

permissions:
  contents: read

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'

      - uses: android-actions/setup-android@v3

      - name: Install Android SDK
        run: |
          yes | sdkmanager --licenses
          sdkmanager "platforms;android-34" "build-tools;34.0.0"

      - uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: '8.7'

      - name: Build APK
        run: gradle --no-daemon :app:assembleDebug

      - uses: actions/upload-artifact@v4
        with:
          name: Naira-AI-APK
          path: app/build/outputs/apk/debug/app-debug.apk
          if-no-files-found: error
YAML

BRANCH=$(git branch --show-current)
[ -n "$BRANCH" ] || BRANCH=main

git add -A

if ! git diff --cached --quiet; then
    git config user.name "Naira AI Builder"
    git config user.email "naira-builder@example.com"
    git commit -m "Prepare Naira AI APK build"
fi

echo "GitHub Username likho:"
read -r GH_USER

echo "GitHub Token paste karo (screen par nazar nahi aayega):"
read -rsp "Token: " GH_TOKEN
echo

export GH_USER GH_TOKEN

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"; unset GH_TOKEN' EXIT

cat > "$TMP/askpass" <<'ASK'
#!/data/data/com.termux/files/usr/bin/sh
case "$1" in
  *Username*) echo "$GH_USER" ;;
  *) echo "$GH_TOKEN" ;;
esac
ASK

chmod 700 "$TMP/askpass"

echo "Project GitHub par upload ho raha hai..."

GIT_ASKPASS="$TMP/askpass" GIT_TERMINAL_PROMPT=0 \
git -c credential.helper= push -u origin "$BRANCH"

echo "APK Build shuru ho raha hai..."

CODE=$(curl -sS -o "$TMP/result" -w "%{http_code}" \
  -X POST \
  -H "Accept: application/vnd.github+json" \
  -H "Authorization: Bearer $GH_TOKEN" \
  -H "X-GitHub-Api-Version: 2022-11-28" \
  "https://api.github.com/repos/$OWNER/$NAME/actions/workflows/android-apk.yml/dispatches" \
  -d "{\"ref\":\"$BRANCH\"}")

if [ "$CODE" = "204" ]; then
    echo "SUCCESS: APK BUILD STARTED!"
    echo "https://github.com/$OWNER/$NAME/actions"
else
    echo "GitHub error: $CODE"
    cat "$TMP/result"
    exit 1
fi
