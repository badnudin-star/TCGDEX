# Self-hosted set images

`tools/catalog-builder/collect_set_images.py` (run by the weekly catalog workflow) fills
this directory with the set logo/symbol images the catalog references and rewrites the
catalog URLs to stable jsDelivr URLs:

https://cdn.jsdelivr.net/gh/badnudin-star/TCGDEX@main/set-images/{setId}.symbol.png

Sources: TCGdex (assets.tcgdex.net) for everything it serves, plus the Bulbapedia
Archives MediaWiki API as a best-effort fallback for sets without a symbol image.
Sets without any image fall back to the abbreviation badge in the app.

This directory is committed automatically by `.github/workflows/catalog.yml`
with `[skip ci]` — do not edit images by hand.
