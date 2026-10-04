#!/usr/bin/env bash
# Compile, installe et ouvre « Audit ».
#
#   ./pousser.sh          le téléphone s'il répond, l'émulateur sinon
#   ./pousser.sh tel      le téléphone, et rien d'autre
#   ./pousser.sh emu      l'émulateur, et rien d'autre
#   ./pousser.sh <id>     un appareil précis (cf. adb devices)
#
# Second argument : « release » (défaut) ou « debug ». La release est signée avec
# cle-maison.jks, donc elle se met à jour par-dessus la précédente sans effacer
# les audits ; la debug s'installe à côté, sous le nom « Audit (test) ».

set -euo pipefail

ADB="$HOME/Android/Sdk/platform-tools/adb"
RACINE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PAQUET="fr.pouik.audit"

cible="${1:-auto}"
variante="${2:-release}"

# Le téléphone se connecte en débogage sans fil : tant qu'il n'a pas été joint une
# fois, il n'apparaît pas dans `adb devices` — seulement dans l'annonce mDNS. On va
# donc le chercher là avant de renoncer.
reveiller_le_sans_fil() {
  local annonce
  annonce="$("$ADB" mdns services 2>/dev/null | grep -m1 '_adb-tls-connect' | awk '{print $NF}')" || true
  [ -n "${annonce:-}" ] && "$ADB" connect "$annonce" >/dev/null 2>&1 || true
}

# Le même appareil se présente deux fois une fois joint (son nom mDNS et son
# adresse) : `head -1` suffit, les deux mènent au même téléphone.
trouver_tel() {
  "$ADB" devices | grep -v '^List' | grep -vE '^emulator-|^\s*$' | grep -m1 'device$' | cut -f1 || true
}
trouver_emu() { "$ADB" devices | grep -m1 '^emulator-' | cut -f1 || true; }

case "$cible" in
  auto)
    appareil="$(trouver_tel)"
    if [ -z "$appareil" ]; then reveiller_le_sans_fil; sleep 1; appareil="$(trouver_tel)"; fi
    [ -z "$appareil" ] && appareil="$(trouver_emu)"
    ;;
  tel)
    appareil="$(trouver_tel)"
    if [ -z "$appareil" ]; then reveiller_le_sans_fil; sleep 1; appareil="$(trouver_tel)"; fi
    ;;
  emu) appareil="$(trouver_emu)" ;;
  *)   appareil="$cible" ;;
esac

if [ -z "${appareil:-}" ]; then
  echo "Aucun appareil." >&2
  echo "Téléphone : Options de développement → Débogage sans fil, sur le même Wi-Fi." >&2
  echo "Émulateur : ~/Android/Sdk/emulator/emulator -avd Fold8_Ouvert" >&2
  exit 1
fi

case "$variante" in
  release) tache="assembleRelease"; apk="$RACINE/app/build/outputs/apk/release/app-release.apk"; paquet="$PAQUET" ;;
  debug)   tache="assembleDebug";   apk="$RACINE/app/build/outputs/apk/debug/app-debug.apk";     paquet="$PAQUET.test" ;;
  *) echo "Variante inconnue : $variante (release ou debug)" >&2; exit 1 ;;
esac

echo "→ compilation ($variante)"
(cd "$RACINE" && ./gradlew "$tache" --console=plain -q)

echo "→ installation sur $appareil"
# -t pour la debug : AGP la marque « test only », et l'installation la refuse sinon.
if [ "$variante" = "debug" ]; then
  "$ADB" -s "$appareil" install -r -t "$apk" | tail -1
else
  "$ADB" -s "$appareil" install -r "$apk" | tail -1
fi

echo "→ ouverture"
"$ADB" -s "$appareil" shell am start -n "$paquet/$PAQUET.MainActivity" > /dev/null

echo "✓ fait"
