import json, re, pathlib

p = pathlib.Path(__file__).parent
j = json.loads((p / "gamemaster.json").read_text())
cpms = json.loads(
    "["
    + re.search(r"var cpms = \[(.*?)\]", (p / "pokemon.js").read_text(), re.S)[1]
    + "]"
)[:99]
moves = {x["moveId"]: x["name"] for x in j["moves"]}
ranks = {
    k: json.loads((p / (k + ".json")).read_text()) for k in ["great", "ultra", "master"]
}
lookup = {
    k: {x["speciesId"]: (i + 1, x) for i, x in enumerate(v)} for k, v in ranks.items()
}
ps = []
for x in j["pokemon"]:
    q = {
        "name": x["speciesName"],
        "id": x["speciesId"],
        "stats": [x["baseStats"][k] for k in ["atk", "def", "hp"]],
    }
    q["leagues"] = {}
    for k, items in lookup.items():
        if x["speciesId"] in items:
            rank, r = items[x["speciesId"]]
            q["leagues"][k] = {
                "rank": rank,
                "total": len(ranks[k]),
                "score": r["score"],
                "moves": [
                    moves.get(m, m)
                    + (" (Elite/event)" if m in x.get("eliteMoves", []) else "")
                    for m in r.get("moveset", [])
                ],
            }
    ps.append(q)
# Explicit aliases only; do not silently collapse forms, shadows or costumes.
for x in ps:
    if x["id"] == "mewtwo_armored":
        x["alias"] = "Armored Mewtwo"
out = {
    "date": "2026-09-08",
    "sourceTimestamp": j.get("timestamp"),
    "cpms": cpms,
    "pokemon": ps,
}
pathlib.Path("go-coach/app/src/main/assets/pvp.json").write_text(
    json.dumps(out, separators=(",", ":"))
)
print("Exported", len(ps), "species/form entries and", len(cpms), "CP multipliers")
for k, rs in lookup.items():
    print(k, rs.get("cinderace", ("Unranked",))[0])
