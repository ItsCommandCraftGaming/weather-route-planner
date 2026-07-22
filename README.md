# Weather Route Planner (Planificator de Rute în funcție de Vreme)

O aplicație Android modernă, construită în **Kotlin** și **Jetpack Compose**, proiectată pentru a ajuta șoferii să aleagă cea mai optimă și mai sigură rută către destinație. Aplicația utilizează date meteorologice în timp real și algoritmi de analiză geografică pentru a calcula un scor de siguranță (cost) pentru mai multe rute alternative.

---

## 🚀 Caracteristici Principale și Detalii de Implementare

### 🗺️ 1. Rute Alternative Multiple și Trafic în Timp Real
* **Funcționalitate:** La căutarea unei destinații, aplicația solicită și afișează simultan până la **3 rute alternative** calculate cu profilul de trafic auto.
* **Ajustare ETA:** Durata călătoriei (ETA) este actualizată automat în timp real în funcție de condițiile de trafic, aglomerație și eventuale întârzieri rutiere.
* **Colorare dinamică pe segmente:** Linia rutei este colorată conform adnotărilor de congestie:
  * 🟢 **Verde (`#4CAF50`):** Trafic fluid / liber
  * 🟡 **Galben (`#FFC107`):** Trafic moderat
  * 🟠 **Portocaliu (`#FF5722`):** Trafic aglomerat
  * 🔴 **Roșu închis (`#B71C1C`):** Congestie severă
* **Contur rute:** Traseul selectat are un contur albastru, în timp ce traseele alternative ne-selectate rămân reprezentate cu linii de culoare **gri**.
* **Implementare:** 
  * Integrat în [MapScreen.kt](file:///D:/Android_studio_projects/app/src/main/java/com/example/myapplication/ui/components/MapScreen.kt) prin intermediul **Mapbox Navigation SDK**, folosind profilul `PROFILE_DRIVING_TRAFFIC` și parametrul `alternatives(true)`.
  * Segmentele de trafic sunt extrase din proprietățile de congestie (`ANNOTATION_CONGESTION`) ale rutei geometrice și desenate pe hartă folosind straturi `lineLayer` din Mapbox.

---

### 🌡️ 2. Evaluarea Severității Meteo (Scor Inteligent)
* **Funcționalitate:** Fiecare rută primește un scor de cost calculat după formula:
  $$\text{Cost} = \text{Distanța (km)} + (\text{Factor Meteo} \times \text{Timp (minute)})$$
  Acest calcul favorizează traseele cu vreme bună, dar ține cont și de lungimea ocolului (dacă ocolul pe vreme bună este prea lung, drumul mai scurt cu ploaie ușoară poate rămâne mai avantajos).
* **Determinarea penalizărilor:** Penalizările sunt calculate dinamic în funcție de intensitatea precipitațiilor:
  * ☀️ **Cer senin / Nori parțiali:** factor de bază ($1.0$ - $1.1$)
  * ☁️ **Nori denși:** factor $1.2$
  * 🌫️ **Ceață:** factor $2.5$
  * 🌧️ **Ploaie:** factor dinamic: $1.8 + (\text{precipitații în mm/h} \times 1.5)$
  * ❄️ **Zăpadă:** factor dinamic: $3.5 + (\text{ninsoare în mm/h} \times 2.0)$
* **Implementare:**
  * **Eșantionare Geografică:** Modulul [RouteWeatherScannerImpl.kt](file:///D:/Android_studio_projects/app/src/main/java/com/example/myapplication/data/classes/RouteWeatherScannerImpl.kt) (implementarea interfeței [IRouteWeatherScanner](file:///D:/Android_studio_projects/app/src/main/java/com/example/myapplication/data/interfaces/IRouteWeatherScanner.kt)) împarte geometric traseul în puncte de verificare (eșantionare spațio-temporală) și estimează ora exactă la care vehiculul se va afla în acel punct.
  * **Preluare Prognoză:** Datele meteorologice în timp real sunt descărcate asincron prin repository-ul [OpenMeteoWeatherRepository.kt](file:///D:/Android_studio_projects/app/src/main/java/com/example/myapplication/data/classes/OpenMeteoWeatherRepository.kt) (care implementează [IWeatherRepository](file:///D:/Android_studio_projects/app/src/main/java/com/example/myapplication/data/interfaces/IWeatherRepository.kt)) ce interoghează API-ul public **Open-Meteo**.

---

### 🌌 3. Detecția Tranzitului Zi/Noapte și Alerte Twilight
* **Funcționalitate:** Calculează poziția soarelui de-a lungul rutei și desenează pe hartă zonele de umbră ale Pământului (amurg civil, amurg nautic, amurg astronomic și noapte deplină). De asemenea, plasează pini de avertizare pe hartă în punctele unde șoferul va intra în întuneric.
* **Implementare:**
  * **Algoritm Astronomic:** Realizat în [SunCalculator.kt](file:///D:/Android_studio_projects/app/src/main/java/com/example/myapplication/data/classes/SunCalculator.kt) pe baza declinației solare și a timpului UTC în milisecunde.
  * **Poligoane Mapbox (`Polygon`):** Funcția `calculeazaUmbra` generează obiecte `Polygon` reprezentând conturul umbrei Pământului la altitudinile solare de $0.0^\circ$ (amurg civil), $-6.0^\circ$ (nautic), $-12.0^\circ$ (astronomic) și $-18.0^\circ$ (noapte).
  * **Afișare:** Straturile sunt randate ca `fillColor` peste hartă în [MapScreen.kt](file:///D:/Android_studio_projects/app/src/main/java/com/example/myapplication/ui/components/MapScreen.kt), cu culori de umplere semi-transparente de opacitate crescătoare.

---

### 🚗 4. Simulare în Timp Real a Deplasării
* **Funcționalitate:** Permite rularea unui simulator vizual în care un punct animat parcurge traseul în timp real, actualizând distanța, timpul rămas și transmițând dacă vehiculul este la timp sau în întârziere.
* **Implementare:**
  * Realizată în [DrivingSimulatorImpl.kt](file:///D:/Android_studio_projects/app/src/main/java/com/example/myapplication/data/classes/DrivingSimulatorImpl.kt) (implementând interfeța [IDrivingSimulator](file:///D:/Android_studio_projects/app/src/main/java/com/example/myapplication/data/interfaces/IDrivingSimulator.kt)).
  * Folosește funcția `TurfMeasurement.along` din biblioteca Mapbox Turf pentru a interpola coordonata exactă a mașinii de-a lungul rutei geometrice în funcție de secundele scurse.

---

### 🎚️ 5. Slider de Timp (Time Scrubbing & Prognoză Viitoare)
* **Funcționalitate:** Permite utilizatorului să gliseze de-a lungul timpului estimat de călătorie (slider de timp) pentru a vedea cum se vor schimba vremea, luminozitatea și poziția umbrei pe traseu în viitor.
* **Implementare:**
  * Integrat direct în interfața grafică Jetpack Compose din [MapScreen.kt](file:///D:/Android_studio_projects/app/src/main/java/com/example/myapplication/ui/components/MapScreen.kt).
  * La deplasarea slider-ului, se modifică timpul de referință în [MapState.kt](file:///D:/Android_studio_projects/app/src/main/java/com/example/myapplication/ui/state/MapState.kt), determinând recalcularea instantanee a unghiurilor solare și reactualizarea pozițiilor straturilor de umbră.

---

### 🌧️ 6. Radar Meteo Dinamic (RainViewer API)
* **Funcționalitate:** Afișează un strat radar dinamic peste hartă pentru precipitații (ploaie/ninsoare), actualizat în mod corespunzător în funcție de intervalul de timp selectat pe slider.
* **Implementare:**
  * **Interogare date:** [OpenMeteoWeatherRepository.kt](file:///D:/Android_studio_projects/app/src/main/java/com/example/myapplication/data/classes/OpenMeteoWeatherRepository.kt) interoghează API-ul **RainViewer** pentru a obține timestamp-urile radar valide.
  * **Suprapunere pe hartă:** Se adaugă straturi de tip `RasterLayer` în stilul hărții Mapbox, încărcând tile-urile radar dinamic la modificarea timpului din slider.

---

### ⏱️ 7. Zona de Siguranță (Safe Zone Mode)
* **Funcționalitate:** Permite utilizatorului să specifice o oră de sosire limită. Aplicația desenează un cerc în jurul destinației care se micșorează în timp real. Dacă simulatorul rămâne în afara cercului (indicând că vehiculul este în întârziere), se trasează o linie roșie de la mașină la marginea cercului și se afișează timpul de întârziere în secunde.
* **Implementare:**
  * Gestionată de [SafeZoneManagerImpl.kt](file:///D:/Android_studio_projects/app/src/main/java/com/example/myapplication/data/classes/SafeZoneManagerImpl.kt) (care implementează [ISafeZoneManager](file:///D:/Android_studio_projects/app/src/main/java/com/example/myapplication/data/interfaces/ISafeZoneManager.kt)).
  * **Calcule Geometrice (Turf):** Folosește `TurfMeasurement.distance` pentru distanța față de centru, `TurfMeasurement.destination` pentru calculul marginii cercului și `TurfMeasurement.midpoint` pentru poziționarea etichetei cu secunde.

---

### 🚨 8. Incidente de Trafic în Timp Real (TomTom Incidents API)
* **Funcționalitate:** Detectează și plasează pe hartă pictograme specifice pentru incidentele rutiere curente (accidente, drum blocat, ceață densă, lucrări) din vecinătatea traseelor.
* **Implementare:**
  * **Preluare Date:** Clasa [TomTomIncidentRepositoryImpl.kt](file:///D:/Android_studio_projects/app/src/main/java/com/example/myapplication/data/classes/TomTomIncidentRepositoryImpl.kt) (implementând [ITomTomIncidentRepository](file:///D:/Android_studio_projects/app/src/main/java/com/example/myapplication/data/interfaces/ITomTomIncidentRepository.kt)) apelează serviciul **TomTom Traffic Incident Details** folosind coordonatele bounding box (BBOX) ale rutei.
  * **Afișare:** Randează pictograme interactive pe hartă în [MapScreen.kt](file:///D:/Android_studio_projects/app/src/main/java/com/example/myapplication/ui/components/MapScreen.kt), asociate cu descrierile incidentelor.

---

### 🔍 9. Căutare Destinații și Istoric Persistent
* **Funcționalitate:** Casetă de căutare interactivă cu auto-completare de adrese, plus salvarea ultimelor 5 căutări efectuate pentru acces rapid.
* **Implementare:**
  * Căutarea este realizată prin componenta [SearchBar.kt](file:///D:/Android_studio_projects/app/src/main/java/com/example/myapplication/ui/components/SearchBar.kt) integrată cu **Mapbox Search SDK / Geocoding**.
  * Istoricul este gestionat de [SharedPrefsSearchHistoryRepository.kt](file:///D:/Android_studio_projects/app/src/main/java/com/example/myapplication/data/classes/SharedPrefsSearchHistoryRepository.kt) (care implementează [ISearchHistoryRepository](file:///D:/Android_studio_projects/app/src/main/java/com/example/myapplication/data/interfaces/ISearchHistoryRepository.kt)) folosind stocarea locală **SharedPreferences** din SDK-ul Android.

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
TOMTOM_API_KEY=your_tomtom_api_key
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
