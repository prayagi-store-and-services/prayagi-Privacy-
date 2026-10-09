# Third-party lists used for site blocking

SensorGuard's "Block adult and gambling sites" uses a bundled list of domain names built at build time by `scripts/build_blocklist.py`. The build writes `SOURCES.txt` next to the list with the build date, the number of names and the sha256 of every downloaded file.

## BlockList Project (porn.txt, gambling.txt)
- Source: https://github.com/blocklistproject/Lists
- Licence: the repository LICENSE is the Unlicense (public domain). The porn.txt file header states "License: MIT". We follow the stricter of the two and credit it under the MIT licence.
- Credit: "BlockList Project (blocklistproject), https://github.com/blocklistproject/Lists".
- Changes by us: kept only the domain names, merged, removed names whose parent domain is already listed, sorted, compressed.
- MIT licence text: Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions: The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software. THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND.

## UT1 blacklists (gambling category)
- Source: https://dsi.ut-capitole.fr/blacklists/ (Universite Toulouse Capitole). File used: gambling.tar.gz, member `gambling/domains`.
- Licence: Creative Commons Attribution-ShareAlike 4.0 International (CC BY-SA 4.0), https://creativecommons.org/licenses/by-sa/4.0/ (the UT1 page links this licence).
- Credit: "UT1 blacklists, Universite Toulouse Capitole, https://dsi.ut-capitole.fr/blacklists/".
- Changes by us: same as above (names only, merged, parent-reduced, sorted, compressed). The merged list file is therefore an adapted work: the data file `blocklist/domains.txt.gz` is offered under CC BY-SA 4.0 for the UT1 part. The app code stays under the repository's own licence; the list is a separate data file.
- The UT1 adult category is NOT included in this version (4.6 million names, 17 MB); it can be switched on in the build script (`INCLUDE_UT1_ADULT=1`) after a size decision.

## Freshness
The BlockList files carry their own dates (porn.txt "Last modified 2026-07-18", gambling.txt "Date 2026-07-20" at the time of the first build). The list inside each release is a snapshot from the build date shown in `SOURCES.txt`.
