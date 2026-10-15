#!/usr/bin/env sh
# Checks how the site answers known pages, unknown pages, old sl-tours.com addresses and search-engine files.
# Read-only: sends GET requests only. Usage: tools/smoke/routes.sh [base-url] [indexing: true|false]
#   tools/smoke/routes.sh http://localhost:8080 false
set -u
BASE="${1:-http://localhost:8080}"
INDEXING="${2:-false}"
failures=0

status() { curl -s -o /dev/null -w '%{http_code}' "$BASE$1"; }
location() { curl -s -o /dev/null -w '%{redirect_url}' "$BASE$1"; }
check() { # description expected actual
  if [ "$2" = "$3" ]; then echo "ok    $1"; else echo "FAIL  $1: expected $2, got $3"; failures=$((failures + 1)); fi
}

for page in / /experiences /experiences/cape-peninsula-journey /explore /book-private-trip /about /contact /privacy /login; do
  check "page $page" 200 "$(status "$page")"
done
for page in /no-such-page /experiences/no-such-tour /tours/unknown; do
  check "unknown $page" 404 "$(status "$page")"
done
check "unknown page is noindex" 1 "$(curl -s "$BASE/no-such-page" | grep -c 'name="robots" content="noindex"')"
check "missing asset" 404 "$(status /assets/no-such-file.js)"

while read -r old new; do
  check "redirect $old" "301 $BASE$new" "$(status "$old") $(location "$old")"
done <<'EOF'
/tours /experiences
/tours/cape-point /experiences/cape-peninsula-journey
/tours/city-tour /experiences/cape-town-essentials
/tours/winelands-tour /experiences/cape-winelands-escape
/tours/township-tour /experiences/cape-culture-experience
/tours/garden-route /experiences/garden-route-journey
/tours/airport-transfers /experiences/airport-transfer
/faq /contact
/testimonials /#reviews
/our_tours.html /experiences
EOF
check "redirect keeps query string" "$BASE/experiences?utm_source=x" "$(location '/tours?utm_source=x')"

home=$(curl -s "$BASE/")
robots=$(curl -s "$BASE/robots.txt")
if [ "$INDEXING" = "true" ]; then
  check "home indexable" 0 "$(printf '%s' "$home" | grep -c 'content="noindex"')"
  check "sitemap" 200 "$(status /sitemap.xml)"
  check "robots advertises sitemap" 1 "$(printf '%s' "$robots" | grep -c '^Sitemap:')"
else
  check "home noindex (staging)" 1 "$(printf '%s' "$home" | grep -c 'name="robots" content="noindex"')"
  check "sitemap hidden (staging)" 404 "$(status /sitemap.xml)"
  check "robots has no sitemap (staging)" 0 "$(printf '%s' "$robots" | grep -c '^Sitemap:')"
fi
check "canonical names production host" 1 "$(curl -s "$BASE/about" | grep -c 'rel="canonical" href="https://sl-tours.com/about"')"

echo "$failures failure(s)"
[ "$failures" -eq 0 ]
