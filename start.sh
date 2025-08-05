#!/bin/bash

# Build the Java backend
echo "Building Java backend..."
mvn clean package

# Start the Java backend in the background with optimized memory settings for 2GB instance
echo "Starting Java backend with optimized memory settings..."
java -Xmx1536m -Xms512m -XX:+UseG1GC -jar target/p2p-1.0-SNAPSHOT.jar &
BACKEND_PID=$!

# Wait for the backend to start
echo "Waiting for backend to start..."
sleep 5

# Install frontend dependencies if node_modules doesn't exist
if [ ! -d "ui/node_modules" ]; then
  echo "Installing frontend dependencies..."
  cd ui && npm install && cd ..
fi

# Start the frontend
echo "Starting frontend..."
cd ui && npm start

# When the frontend is stopped, also stop the backend
echo "Stopping backend (PID: $BACKEND_PID)..."
kill $BACKEND_PID