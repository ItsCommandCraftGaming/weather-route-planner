# Weather Route Planner (Planificator de Rute în funcție de Vreme)

O aplicație Android modernă, construită în **Kotlin** și **Jetpack Compose**, proiectată pentru a ajuta șoferii să aleagă cea mai optimă și mai sigură rută către destinație. Aplicația utilizează date meteorologice în timp real și algoritmi de analiză geografică pentru a calcula un scor de siguranță (cost) pentru mai multe rute alternative.

---

## 🚀 Caracteristici Principale

### 🗺️ Rute Alternative Multiple și Trafic în Timp Real
* La căutarea unei destinații, aplicația solicită și afișează simultan până la **3 rute alternative** calculate cu profilul Mapbox **Driving-Traffic** (`PROFILE_DRIVING_TRAFFIC`).
* **Durata călătoriei (ETA)** este ajustată automat în timp real în funcție de condițiile de trafic, aglomerație și eventuale întârzieri rutiere.
* **Linia rutei este colorată dinamic pe segmente** conform adnotărilor de congestie (`ANNOTATION_CONGESTION`):
  * 🟢 **Verde (`#4CAF50`):** Trafic fluid / liber
  * 🟡 **Galben (`#FFC107`):** Trafic moderat
  * 🟠 **Portocaliu (`#FF5722`):** Trafic aglomerat
  * 🔴 **Roșu închis (`#B71C1C`):** Congestie severă
* Traseul selectat are un contur stilizat cu bordură albastră pentru lizibilitate maximă, iar traseele alternative ne-selectate rămân reprezentate cu linii de culoare **gri**.

### 🌡️ Evaluarea Severității Meteo (Scor Inteligent)
Fiecare rută primește un scor de cost calculat după formula:
$$\text{Cost} = \text{Distanța (km)} + (\text{Factor Meteo} \times \text{Timp (minute)})$$

* **Factorul Meteo** este media penalizărilor punctelor scanate pe traseu.
* Penalizările sunt calculate **dinamic în funcție de intensitatea precipitațiilor** primite de la API-ul Open-Meteo:
  * ☀️ **Cer senin / Nori parțiali:** factor de bază ($1.0$ - $1.1$)
  * ☁️ **Nori denși:** factor $1.2$
  * 🌫️ **Ceață:** factor $2.5$
  * 🌧️ **Ploaie:** factor dinamic: $1.8 + (\text{precipitații în mm/h} \times 1.5)$
  * ❄️ **Zăpadă:** factor dinamic: $3.5 + (\text{ninsoare în mm/h} \times 2.0)$
* Acest calcul favorizează traseele cu vreme bună, dar ține cont și de lungimea ocolului (dacă ocolul pe vreme bună este prea lung, drumul mai scurt cu ploaie ușoară poate rămâne mai avantajos).

### 🌌 Detecția Tranzitului Zi/Noapte
* Analizează traseul folosind formule astronomice pentru a calcula altitudinea soarelui în funcție de coordonate și ora estimată la care mașina se va afla în acel punct.
* Desenează linii de tranzitie pe hartă și plasează pin-uri de avertizare pentru crepusculul civil, crepusculul nautic, astronomic sau noaptea deplină.

### 🚗 Simulare în Timp Real și Scrubbing (Prognoză Viitoare)
* **Simulator de condus:** Un punct animat parcurge traseul în timp real, iar aplicația actualizează distanța/timpul rămase.
* **Slider-ul de timp (Scrubbing):** Permite utilizatorului să gliseze de-a lungul timpului de călătorie (până la ora estimată a sosirii) pentru a vedea cum se vor schimba vremea și luminozitatea pe traseu în viitor.
* **Hărți Radar RainViewer:** Afișează un strat radar dinamic pentru ploaie, actualizat în funcție de intervalul de timp selectat pe slider.

### ⏱️ Zona de Siguranță (Safe Zone Mode)
* Permite utilizatorului să configureze o oră de sosire limită.
* Aplicația generează un cerc de siguranță în jurul destinației care se micșorează în timp real, oferind un feedback vizual rapid referitor la marja de timp disponibilă pentru a ajunge în siguranță.

---

## 🛠️ Arhitectură și Structură Fișiere

Aplicația respectă principiile programării declarative din Jetpack Compose și este structurată astfel:

* 📱 **`MainActivity.kt`**: Punctul de intrare în aplicație; configurează token-ul Mapbox și inițializează starea.
* 📦 **`MapState.kt`**: Gestionarul principal de stare (`MapState`), care conține logica pentru coordonarea hărții, istoricul căutărilor, controlul simulatorului, interfața Safe Zone și declanșarea asincronă a scanării meteo pe cele 3 rute alternative.
* 🎨 **`MapScreen.kt`**: Interfața vizuală completă; controlează straturile Mapbox, desenează traseele, populează pin-urile de alertă, plasează selectorul de rute și cardurile cu detalii de cost de sub bara de căutare.
* 📡 **`OpenMeteoWeatherRepository.kt`**: Repository-ul care comunică direct cu API-ul RainViewer și cu API-ul Open-Meteo, extrăgând prognoza orară detaliată (precipitații, ninsoare, nori, vizibilitate).
* 🔍 **`RouteWeatherScannerImpl.kt`**: Modulul responsabil cu eșantionarea spațio-temporală a traseelor geometrice (LineString) pentru a detecta alertele meteo și fazele de lumină în mod asincron.

---

## ⚙️ Configurare Proiect

Pentru a rula proiectul pe dispozitivul propriu sau într-un emulator, ai nevoie de un token de acces Mapbox.

1. Creează un fișier numit `local.properties` în directorul rădăcină al proiectului (dacă nu există deja).
2. Adaugă următoarele proprietăți cu cheile tale Mapbox:

```ini
MAPBOX_PUBLIC_TOKEN=pk.your_mapbox_public_token
MAPBOX_SECRET_TOKEN=sk.your_mapbox_secret_token
RAINBOW_API_KEY=your_rainbow_api_key
```

3. Deschide proiectul în Android Studio și rulează build-ul Gradle.

---

## 🧮 Exemplu de Scenariu de Routing
Imaginează-zi două rute către aceeași destinație:
1. **Ruta 1 (Autostradă - 50 km, durează 30 de minute):** Plouă torențial ($5\text{ mm/h}$). 
   * Factorul Meteo devine $1.8 + (5 \times 1.5) = 9.3$. 
   * $\text{Scor/Cost} = 50 + (9.3 \times 30) = 329.0$.
2. **Ruta 2 (Drum Național ocolitor - 65 km, durează 50 de minute):** Cer senin.
   * Factorul Meteo este $1.0$. 
   * $\text{Scor/Cost} = 65 + (1.0 \times 50) = 115.0$.

Deși Ruta 2 este mai lungă ca distanță și timp, ea are un scor/cost mult mai mic ($115.0$ față de $329.0$) datorită condițiilor ideale de drum, fiind recomandată ca opțiune mult mai sigură pentru călătorie.
