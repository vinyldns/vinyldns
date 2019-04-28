VERSION=$(cat version.sbt)

IN_PARENS=$(echo "$VERSION" | sed -n 's/.*"\(.*\)".*/\1/p')

NO_SNAPSHOT="${IN_PARENS//-SNAPSHOT}"

echo "$NO_SNAPSHOT"

