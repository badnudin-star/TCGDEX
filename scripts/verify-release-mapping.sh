#!/usr/bin/env bash
set -euo pipefail

mapping_file="${1:-app/build/outputs/mapping/release/mapping.txt}"
if [[ ! -f "$mapping_file" ]]; then
  echo "ERROR: R8 mapping file not found: $mapping_file" >&2
  exit 1
fi

# Gson/Retrofit models are reflected at runtime; their names must survive R8.
classes=(
  'com.pokemontcg.sammlung.data.api.TCGdexApi'
  'com.pokemontcg.sammlung.data.api.CardBrief'
  'com.pokemontcg.sammlung.data.api.CardData'
  'com.pokemontcg.sammlung.data.api.SetDataBrief'
  'com.pokemontcg.sammlung.data.api.SetData'
  'com.pokemontcg.sammlung.data.api.SerieBrief'
  'com.pokemontcg.sammlung.data.api.CardCount'
  'com.pokemontcg.sammlung.data.api.Pricing'
  'com.pokemontcg.sammlung.data.api.CardmarketPrice'
  'com.pokemontcg.sammlung.data.api.TcgplayerPrice'
  'com.pokemontcg.sammlung.data.api.PriceVariant'
  'com.pokemontcg.sammlung.data.api.PokeApi'
  'com.pokemontcg.sammlung.data.api.PokemonListResponse'
  'com.pokemontcg.sammlung.data.api.PokemonResult'
)

for class_name in "${classes[@]}"; do
  if ! grep -Fqx -- "$class_name -> $class_name:" "$mapping_file"; then
    echo "ERROR: R8 renamed or omitted required API model: $class_name" >&2
    exit 1
  fi
done

echo "OK: all ${#classes[@]} Gson/Retrofit API types retain their names in R8 mapping."
