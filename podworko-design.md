# Mapa Unfair "Kurczak" — etap 1: Podwórko (projekt)

## Cel
Mapa przygodowa do Minecrafta w stylu "Polskie Unfair" z kanału Enzzi (EnzziGaming). Motyw: kurczak. Pierwszy etap uczy gracza, że każda pozornie prosta rzecz jest pułapką. Etap ma być śmieszny i frustrujący, ale uczciwy: jest dokładnie jedno rozwiązanie.

## Cele / nie-cele
- Cel: w pełni działający etap 1 w trybie przygodowym, z trzema ścieżkami śmierci i jedną poprawną.
- Cel: nic na mapie nie da się zniszczyć poza piorunochronami (kilof z tagiem `CanDestroy`).
- Nie-cel (na razie): etapy 2 i dalsze (Kurnik, pole kukurydzy, Młyn, Staw, boss lis).
- Anty-cel: pułapki, których nie da się przewidzieć po fakcie. Gracz po śmierci ma myśleć "no tak", nie "to błąd mapy".

## Ograniczenia
- Minecraft Java (założenie, do potwierdzenia wersji).
- Tryb przygodowy na całej mapie, brak niszczenia i stawiania bloków.
- Bez checkpointów w całym etapie.

## Podejście (moja decyzja)
Datapack (funkcje komend) plus świat z wybudowanym etapem. Odrzucone: command blocki w świecie (trudne do utrzymania i testowania), mod (wymusza instalację u gracza, a Enzzi gra w wanilii).

## Układ (z lotu ptaka, ~21 x 14 bloków + wyjście)
Numery zgodne z szkicem.
1. Spawn gracza, za plecami ściana.
2. Drzewo z tabliczką "Otwórz mnie" i skrzynką pod nim. To też drzewo lądowania.
3. Guzik na ścianie za plecami gracza.
4. Pas strzału kurczaka, jeden blok szerokości, na linii drzewo, gracz, guzik.
5. Piorunochron A na dachu domku farmera (krawędź południowa, w zasięgu ręki z ziemi).
6. Piorunochron B na ziemi, na wschód od spawnu.
7. Piorunochron C na ziemi, fałszywy.
8. Domek farmera z dachem, tuż obok drzewa (przerwa około 1 bloku).
9. Wyjście do Kurnika za domkiem, osiągalne skokiem z dachu.

## Mechaniki i ścieżki
| Akcja gracza | Wynik |
|---|---|
| Otwarcie skrzynki od razu | Wyskakuje kurczak i dziobie na śmierć |
| Kliknięcie guzika (3) za sobą | Śmierć |
| Kliknięcie tabliczki i stanie w pasie strzału | Pojawia się kurczak, pluje kukurydzą w twarz, śmierć (strzał po ok. 1,5 s) |
| Kliknięcie tabliczki i zejście z pasa strzału | Kukurydza leci w guzik, wciska go, kurczak ginie |
| Po tym: otwarcie skrzynki | Kilof z `CanDestroy` tylko na piorunochrony |
| Zniszczenie A (dach) | Piorun w domek, farmer wybiega i zabija widłami; cutscenka, gracz zamrożony |
| Zniszczenie B | Piorunochron pojawia się na głowie gracza, piorun, lewitacja; gracz sam steruje i ląduje na drzewie 2, bez obrażeń od upadku (strefa korony zeruje upadek) |
| Zniszczenie C | Piorun w gracza, śmierć |
| Z drzewa 2 skok na dach (8), potem skok do wyjścia (9) | Przejście do etapu 2 |

## Wykonawca zdecyduje
- Kukurydza jako przedmiot lecący komendami (fizyka nie wciska guzików takimi przedmiotami).
- Zamrożenie gracza w cutscence (unieruchomienie przez skrypt).
- Dobór siły lewitacji i trajektorii lotu B tak, żeby lądowanie na drzewie było możliwe, ale wymagało celowania.

## Otwarte
- Wersja Minecrafta (Java).
- Piorunochron C: proponowana śmierć. Do zmiany na życzenie.
- Dokładne umiejscowienie B po testach lotu.
