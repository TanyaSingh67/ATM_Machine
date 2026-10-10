# Java ATM + React UI

- backend/  : Java (OOP) - Account, Bank, Transaction, custom exceptions + ATMServer (REST API, no libraries needed)
- frontend/ : React (Vite) - only the screen / UI

## Run

Terminal 1 (Java backend, needs JDK 11+):
```
cd backend
javac ATM.java ATMServer.java
java ATMServer
```
(Console version still works: `java ATM`)

Terminal 2 (React frontend, needs Node.js):
```
cd frontend
npm install
npm run dev
```
Open http://localhost:5173  ->  login with account 1001, PIN 1234
