# Podwórko (etap 1 mapy Unfair Kurczak) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Zbudować w pełni grywalny etap 1 mapy Unfair w Minecrafcie Java: drzewo z tabliczką, trzy ścieżki śmierci, jedno rozwiązanie, trzy piorunochrony, farmer i wyjście do Kurnika.

**Architecture:** Datapack (`kurczak_unfair`) z funkcjami komend plus świat budowany funkcją `build_podworko`. Cała logika opiera się na tickowym odpytywaniu stanu świata (`execute if/unless block`) i na tagach i wynikach scoreboardu, bez command blocków i bez modów.

**Tech Stack:** Minecraft Java 1.21.x (wersję potwierdzić z użytkownikiem i ustawić `pack_format`), datapack, mcfunction, scoreboard, display entities.

**Spec:** `podworko-design.md`

## Global Constraints

- Minecraft Java, tryb przygodowy dla wszystkich graczy przez cały czas (funkcja tickowa wymusza `gamemode adventure`).
- Nic na mapie nie da się zniszczyć poza piorunochronami; kilof ma komponent `can_break` tylko na `minecraft:lightning_rod`.
- Brak checkpointów: śmierć zawsze wraca do spawnu (8,65,10) i resetuje stan etapu.
- Motyw: kurczak, język polski we wszystkich napisach.
- Układ (O = róg świata (0,64,0), X na wschód, Z na południe): spawn (8,65,10) zwrócony na północ; ściana z tyłu z=13, guzik (8,66,12); pień drzewa (8,65..70,5), korona promień 3; tabliczka (8,66,6) na południowej ścianie pnia; skrzynka (7,65,6); domek x12..20, z2..8, ściany wys. 3, dach y=68; piorunochron A (17,69,7) na dachu; B (18,65,12); C (13,65,12); platforma wyjścia x21..23, z3..7.

## Review Focus

- Gracz klika tabliczkę wiele razy pod rząd: ma powstać tylko jeden kurczak i jedna kukurydza.
- Gracz umiera w trakcie cutscenki lub lotu: stan, zamrożenie i efekty muszą się zresetować po respawnie.
- Gracz wychodzi z gry w trakcie etapu i wraca: nie może zostać zamrożony ani w stanie lotu bez końca.
- Gracz niszczy piorunochrony w innej kolejności po poprawnym przejściu części etapu: stan musi być spójny.
- Gracz wyrzuci lub straci kilof: nie może blokować dalszej gry ani dawać możliwości niszczenia czegokolwiek innego.

---

### Task 1: Szkielet datapacku i tryb przygodowy

**Files:**
- Create: `kurczak_unfair/pack.mcmeta`
- Create: `kurczak_unfair/data/kurczak/function/load.mcfunction`
- Create: `kurczak_unfair/data/kurczak/function/tick.mcfunction`
- Create: `kurczak_unfair/data/minecraft/tags/function/load.json`, `tick.json`

**Interfaces:**
- Produces: funkcje `kurczak:load` (tworzy scoreboardy `k_state`, `k_timer`) i `kurczak:tick` (wywoływane co tick, tu wymusza `gamemode adventure @a`); obiekt scoreboard `k_state` z wartościami `0`=start, `1`=kurczak wywołany, `2`=kurczak martwy, `3`=skrzynka otwarta.

- [ ] **Step 1: Napisz pack.mcmeta** z `pack_format` zgodnym z wersją użytkownika i opisem "Unfair Kurczak".
- [ ] **Step 2: Zweryfikuj ładowanie.** Run: `/datapack list` w świecie. Expected: `kurczak_unfair` w sekcji enabled.
- [ ] **Step 3: Napisz `load` i `tick`** z tagami funkcji; `tick` wymusza przygodowy.
- [ ] **Step 4: Zweryfikuj tryb.** Run: `/gamemode survival` jako gracz, odczekaj tick. Expected: gracz wraca do adventure.
- [ ] **Step 5: Zapisz kopię** (zip folderu datapacku).

### Task 2: Budowa świata Podwórka

**Files:**
- Create: `kurczak_unfair/data/kurczak/function/build_podworko.mcfunction`

**Interfaces:**
- Produces: `kurczak:build_podworko` stawia na świecie cały etap według układu z Global Constraints (podłoże, ściany, drzewo, domek z drzwiami od południa przy x=14, platforma wyjścia, piorunochrony A, B, C, guzik, skrzynka, tabliczka z tekstem "Otwórz mnie"). Funkcja jest idempotentna (można ją uruchomić ponownie).

- [ ] **Step 1: Napisz test wizualny.** Po uruchomieniu funkcji: `execute if block 17 69 7 minecraft:lightning_rod run say A`, analogicznie B (18,65,12) i C (13,65,12), guzik (8,66,12), skrzynka (7,65,6). Expected: sześć komunikatów.
- [ ] **Step 2: Napisz funkcję** z `fill` i `setblock`; drzewo z pnia i liści (promień 3), domek z płaskim dachem y=68.
- [ ] **Step 3: Uruchom.** Run: `/function kurczak:build_podworko` dwa razy, potem test z Step 1. Expected: wszystkie sześć bloków, bez duplikatów.
- [ ] **Step 4: Sprawdź zasięg.** Stań na (17,65,9) i sprawdź, że piorunochron A (17,69,7) jest w zasięgu kliknięcia (około 4,5 bloku).
- [ ] **Step 5: Zapisz kopię.**

### Task 3: Skrzynka, tabliczka, kurczak i kukurydza (trzy ścieżki)

**Files:**
- Create: `kurczak_unfair/data/kurczak/function/chest_open.mcfunction`, `sign_click.mcfunction`, `corn_fly.mcfunction`, `button_press.mcfunction`, `kill_player.mcfunction`
- Modify: `kurczak_unfair/data/kurczak/function/tick.mcfunction`

**Interfaces:**
- Consumes: `k_state` z Task 1.
- Produces: `kurczak:kill_player` (komunikat, wyzerowanie `k_state`, `tp` na spawn, przywrócenie świata etapu przez `kurczak:build_podworko`); `kurczak:sign_click` (tylko gdy `k_state=0`, ustawia `1`, tworzy kurczaka w (8,65,8), startuje `k_timer` na 30 ticków); `kurczak:corn_fly` (przedmiot kukurydzy leci z kurczaka na południe wzdłuż x=8 do z=12); `kurczak:button_press`.

- [ ] **Step 1: Napisz test.** W grze: otwórz skrzynkę bez wcześniejszego kliknięcia tabliczki. Expected: kurczak, śmierć i powrót na spawn.
- [ ] **Step 2: Zaimplementuj `chest_open`** (przy `k_state=0` śmierć; przy `k_state=2` nadaj kilof z `can_break` tylko na lightning_rod i ustaw `3`).
- [ ] **Step 3: Zaimplementuj `button_press`** wykrywany w `tick` przez stan bloku guzika: gdy guzik wciśnięty przez gracza (stan `1` lub `0`) śmierć; gdy wciśnięty przez kukurydzę (flaga z `corn_fly`) usuń kurczaka i ustaw `k_state=2`.
- [ ] **Step 4: Zaimplementuj `sign_click` i `corn_fly`.** Po 30 tickach: jeśli gracz jest w pasie x=8 (zakres x 7,5 do 8,5) między kurczakiem a ścianą — śmierć; inaczej kukurydza leci do guzika.
- [ ] **Step 5: Przetestuj trzy ścieżki.** Run: (a) skrzynka od razu, (b) guzik od razu, (c) tabliczka i stanie w miejscu, (d) tabliczka i odsunięcie się. Expected: a,b,c śmierć; d kurczak ginie i skrzynka daje kilof.
- [ ] **Step 6: Przetestuj kliknięcie tabliczki trzy razy pod rząd.** Expected: jeden kurczak.
- [ ] **Step 7: Zapisz kopię.**

### Task 4: Piorunochrony A, B i C

**Files:**
- Create: `kurczak_unfair/data/kurczak/function/rod_a.mcfunction`, `rod_b.mcfunction`, `rod_c.mcfunction`, `cutscene_farmer.mcfunction`, `freeze.mcfunction`, `unfreeze.mcfunction`
- Modify: `tick.mcfunction`

**Interfaces:**
- Consumes: `kurczak:kill_player`, `k_state=3`.
- Produces: wykrywanie zniszczenia przez `execute unless block 17 69 7 minecraft:lightning_rod` (analogicznie B i C), tylko gdy `k_state=3`. `rod_a`: piorun w domek, `cutscene_farmer` (farmer wychodzi drzwiami (14,65,8), idzie do gracza, zabija widłami), `freeze`/`unfreeze` unieruchamiają gracza na czas scenki. `rod_b`: piorunochron na głowę gracza, piorun, `levitation` na czas do wyboru wykonawcy; po lądowaniu w strefie korony drzewa (x5..11, y≥69, z2..8) zeruj `fall_distance`. `rod_c`: piorun w gracza, śmierć.

- [ ] **Step 1: Napisz test A.** Zniszcz A kilofem. Expected: piorun w domek, farmer wychodzi i zabija, gracz nie może się ruszać.
- [ ] **Step 2: Zaimplementuj `rod_a` i cutscenę.** Zamrożenie przez skrypt, odmrożenie w `kill_player`.
- [ ] **Step 3: Napisz test B.** Zniszcz B, steruj lotem. Expected: lewitacja, lądowanie na koronie bez obrażeń; lądowanie gdzie indziej to normalne obrażenia od upadku.
- [ ] **Step 4: Zaimplementuj `rod_b`** i strefę zerującą upadek. Dostrój siłę lewitacji tak, żeby lądowanie na drzewie było możliwe, ale wymagało celowania.
- [ ] **Step 5: Zaimplementuj `rod_c`** i przetestuj.
- [ ] **Step 6: Przetestuj kolejność.** Zniszcz B, potem (po powrocie) A, itd. Expected: każdy raz stan jest poprawny po `kill_player`.
- [ ] **Step 7: Zapisz kopię.**

### Task 5: Skok na dach i wyjście do Kurnika

**Files:**
- Create: `kurczak_unfair/data/kurczak/function/exit_reached.mcfunction`
- Modify: `tick.mcfunction`

**Interfaces:**
- Consumes: wynik lądowania z Task 4.
- Produces: `kurczak:exit_reached`, wywoływane, gdy gracz stoi na platformie x21..23, z3..7; ustawia `k_state=4` i wypisuje "Koniec etapu 1" (miejsce na przejście do etapu 2).

- [ ] **Step 1: Napisz test.** Z drzewa skocz na dach, z dachu na platformę. Expected: komunikat i `k_state=4`.
- [ ] **Step 2: Zaimplementuj `exit_reached`** i wywołanie w `tick`.
- [ ] **Step 3: Sprawdź odległości skoków.** Drzewo, dach i platforma muszą być osiągalne zwykłym skokiem (przerwa maks. 1 blok); popraw geometrię w `build_podworko`, jeśli nie.
- [ ] **Step 4: Zapisz kopię.**

### Task 6: Test całościowy i odporność na błędy

**Files:**
- Modify: dowolne funkcje z poprzednich tasków według wyników testów

- [ ] **Step 1: Przejdź etap od początku do końca** poprawną ścieżką (tabliczka, odsunięcie, skrzynka, kilof, B, lądowanie, dach, wyjście). Expected: bez śmierci i bez błędów w logu.
- [ ] **Step 2: Przetestuj punkty z Review Focus:** wielokrotne kliknięcie tabliczki, śmierć w trakcie scenki, wyjście z gry w trakcie, inna kolejność piorunochronów, wyrzucenie kilofa. Expected: stan zawsze wraca do `k_state=0` po `kill_player` i żaden z efektów nie zostaje.
- [ ] **Step 3: Przeczytaj log** (`latest.log`) pod kątem błędów funkcji. Expected: brak.
- [ ] **Step 4: Zapisz kopię i przygotuj paczkę** do testów przez znajomych (świat plus datapack w jednym zipie).
