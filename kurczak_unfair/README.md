# Unfair Kurczak - etap 1: Podworko (datapack, Minecraft Java 1.21.11)

Instalacja: wrzuc folder `kurczak_unfair` do `saves/<swiat>/datapacks/`, wejdz do swiata
i wpisz `/reload`. Daj sobie tag `/tag @s add k_dev` (omija wymuszenie trybu przygodowego),
przejdz w creative i uruchom `/function kurczak:build_podworko`. Potem usun tag
(`/tag @s remove k_dev`) i testuj w trybie przygodowym.

Wersja: 1.21.11 (data pack format 94.1; pack.mcmeta uzywa min_format/max_format = 94).
Jesli gra pisze, ze paczka jest niezgodna, uruchom mimo to albo popraw te dwie liczby.
Skladnia: reguly gry snake_case (np. immediate_respawn), click_event/command na tabliczce,
can_break w skroconej formie, nazwy w SNBT. Wersja 26.3 NIE jest sprawdzona: tam data pack
format to 121.0, a tabliczki wymagaja pola allow_op_features (dodane juz w tabliczce).

Lista testow w grze:
1. Skrzynka od razu -> smierc.
2. Guzik za plecami -> smierc.
3. Tabliczka i stoisz -> smierc od kukurydzy.
4. Tabliczka i odsuwasz sie (x != 8) -> kurczak ginie, w skrzynce jest kilof.
5. Kilofem zniszcz A (dach) -> farmer, zamrozenie, smierc.
6. Kilofem zniszcz B -> piorunochron na glowie, lewitacja, ladowanie na drzewie, skok na dach, skok na platforme -> "Podworko ukonczone".
7. Kilofem zniszcz C -> smierc.
Dostrojenie lotu B: dlugosc/sila lewitacji w `rod_b_hit.mcfunction` (obecnie 6 s, poziom 1).

Zmiany v2: dziupla (barrel) w pniu debu zamiast skrzynki; kurczak z dziupli wyskakuje i dziobie;
dab z gry (`place feature minecraft:oak`); dom z wioski (plains_small_house_1);
piorunochron A na okapie dachu, B na latarni w drzewie (ledwo widoczny), C na ziemi;
kurczaki nic nie dropia (DeathLootTable empty + mob_drops false); jest ekran smierci (immediate_respawn false).
Po aktualizacji: /reload, potem /tag @s add k_dev, /function kurczak:build_podworko (przebudowuje caly teren), /tag @s remove k_dev.
