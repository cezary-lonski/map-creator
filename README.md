# Map Creator (beta)

Autor: **Chicken**

Mod Fabric do Minecrafta **1.21.11** do tworzenia map w stylu "unfair" (jak u Enzziego) bez komend: rozdzka, menu w stylu skrzyni i opcjonalny asystent AI.

> Wersja beta, wiele rzeczy jest jeszcze nietestowanych. Błędy zgłaszaj w Issues.

## Co potrafi
- **Skrypty na blokach**: wywoływacze (klik, stanie, redstone, otwarcie, zniszczenie, po innym zdarzeniu, "jeśli się stały") oraz warunki "wykonał się / nie wykonał się".
- **Zdarzenia**: zmiana i niszczenie bloków, efekty dla gracza, zabicie z własnym napisem śmierci.
- **Moby**: ekwipunek, rzut / atak / marsz, obrażenia, opóźnienie, własny drop, odporność na gracza, własny punkt respu.
- **NPC**: handel, wywołacze (wejście, podniesienie, obrażenia), pojawianie się po triggerze.
- **Efekty kamery**: falowanie, odwrócona, bokiem, rozmycie, pikselowanie, latające okienka, własne efekty.
- **Storage**: pojemność bloków i pamięć zdarzeń.
- **Kreator przedmiotów**, ustawienia, przycisk **Graj** i reguły (utrata przedmiotów, czyszczenie bez dropu, reset mapy po każdej śmierci).
- **Chat AI** (Gemini / ChatGPT / Claude): wpisujesz prompt, AI odpowiada blokami, które klikasz, żeby zastosować.

## Instalacja
1. Fabric Loader 0.20.1+ i Fabric API dla 1.21.11, Java 21.
2. Wrzuć `mapcreator-*.jar` do folderu `mods`.
3. W grze weź rozdzkę z kreatywnego, kliknij prawym przyciskiem na blok i użyj menu.

## Asystent AI
Klucz API wpisujesz w Ustawienia → Asystent AI. Zapisuje się lokalnie w `config/mapcreator-ai.json` i jest wysyłany tylko do wybranego dostawcy.

## Budowanie
```
./gradlew build
```
Gotowy plik: `build/libs/`.

## Licencja
CC0-1.0
