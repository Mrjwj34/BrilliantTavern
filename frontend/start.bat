@echo off
echo starting BrilliantTavern server...
echo.

if not exist node_modules (
    echo installing...
    npm install
    echo.
)

echo starting development server...
echo application will be started at http://localhost:3000 
echo press Ctrl+C to stop
echo.

npm run serve

pause
