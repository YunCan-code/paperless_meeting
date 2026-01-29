# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Paperless Meeting System - A full-stack meeting management application with real-time features including voting, lottery draws, and document synchronization. Built for large-screen displays and tablet devices.

**Tech Stack:**
- Backend: FastAPI + SQLModel + Socket.IO + PostgreSQL/SQLite
- Frontend: Vue 3 + Vite + Element Plus
- Deployment: Docker Compose with Nginx reverse proxy
- Real-time: Socket.IO with Redis for multi-worker support

## Development Commands

### Backend (FastAPI)

**Development mode:**
```bash
cd backend
python main.py
# Runs on http://0.0.0.0:8000 with hot reload
```

**Production mode:**
```bash
cd backend
./run_production.sh
# Uses Gunicorn with 4 workers (configurable via WORKERS env var)
```

**Database:**
- Development: SQLite (`backend/database.db`)
- Production: PostgreSQL (configured via `DATABASE_URL` environment variable)
- Database initialization happens automatically on startup via `create_db_and_tables()`

### Frontend (Vue 3 + Vite)

**Development mode:**
```bash
cd frontend
npm run dev
# Runs on http://localhost:5173
# API proxy configured to http://127.0.0.1:8001 in vite.config.js
```

**Build for production:**
```bash
cd frontend
npm run build
# Output: frontend/dist/
```

**Preview production build:**
```bash
cd frontend
npm run preview
```

### Docker Deployment

**Start all services:**
```bash
docker-compose up -d
# Services: PostgreSQL (db), Redis (redis), Backend (backend), Frontend (frontend)
# Frontend exposed on port 5000
```

**View logs:**
```bash
docker-compose logs -f backend
docker-compose logs -f frontend
```

**Rebuild after code changes:**
```bash
docker-compose up -d --build
```

## Architecture

### Backend Structure

**Core files:**
- `main.py` - FastAPI application entry point, includes lifespan management for vote auto-closer
- `models.py` - SQLModel database models (User, Meeting, Vote, Lottery, etc.)
- `database.py` - Database engine configuration with SQLite/PostgreSQL support
- `socket_manager.py` - Socket.IO server for real-time communication (voting, lottery)
- `vote_auto_closer.py` - Background task to automatically close expired votes

**Routes (`backend/routes/`):**
- `auth.py` - Authentication endpoints
- `users.py` - User management
- `meetings.py` - Meeting CRUD operations
- `meeting_types.py` - Meeting type management
- `notes.py` - Follow-up notes/memos
- `devices.py` - Tablet device management
- `app_updates.py` - Android app update management
- `system_settings.py` - System configuration
- `sync.py` - Document synchronization state (for screen following)
- `vote.py` - Voting functionality HTTP API
- `lottery.py` - Lottery draw HTTP API

**Key models:**
- `User` - Meeting participants with authentication
- `Meeting` - Meeting records with many-to-many relationship to Users
- `Attachment` - Meeting documents (PDFs, etc.)
- `Vote` / `VoteOption` / `UserVote` - Voting system with anonymous/multiple choice support
- `Lottery` / `LotteryWinner` / `LotteryParticipant` - Lottery draw system with persistence
- `MeetingSyncState` - Real-time document page synchronization state
- `Device` - Tablet device registration and monitoring

### Frontend Structure

**Routes (`frontend/src/router/index.js`):**
- `/` - Landing page
- `/admin/*` - Admin dashboard (meetings, users, types, settings, devices, toolbox)
- `/mobile/*` - Mobile/tablet interface (login, home)
- `/vote/bigscreen/:id` - Voting big screen display
- `/lottery/:meetingId` - Lottery big screen display

**Key directories:**
- `src/views/Admin/` - Admin management pages
- `src/views/BigScreen/` - Large screen display components
- `src/views/Mobile/` - Tablet interface
- `src/composables/` - Vue composables (useSidebar, useTheme)
- `src/utils/request.js` - Axios HTTP client with interceptors

**API communication:**
- Base URL: `/api` (proxied to backend in development)
- Axios instance configured in `src/utils/request.js`
- Socket.IO client connects to `/socket.io/` endpoint

### Real-time Features (Socket.IO)

**Socket.IO architecture:**
- Server: `backend/socket_manager.py` with Redis manager for multi-worker support
- Rooms: `meeting_{meeting_id}` for per-meeting broadcasts
- Events:
  - `join_meeting` / `leave_meeting` - Room management
  - `vote_start` / `vote_end` / `vote_update` - Voting broadcasts
  - `lottery_action` - Lottery actions (join, quit, prepare, roll, stop, reset)
  - `lottery_state_change` - Lottery state broadcasts
  - `get_lottery_state` - Client state sync request

**Lottery system:**
- Participants stored in `LotteryParticipant` table (persists across refreshes)
- Winners recorded in `LotteryWinner` table
- States: IDLE → PREPARING → ROLLING → RESULT
- Supports preventing duplicate winners via `is_winner` flag

**Voting system:**
- Votes auto-close after duration expires (background task in `vote_auto_closer.py`)
- Supports anonymous/named voting and single/multiple choice
- Real-time result updates broadcast to all participants

### Database Configuration

**Environment variables:**
- `DATABASE_URL` - PostgreSQL connection string (production)
  - Example: `postgresql://paperless:123456@db:5432/paperless_meeting`
- `REDIS_URL` - Redis connection string for Socket.IO
  - Example: `redis://redis:6379`

**Connection pooling (PostgreSQL):**
- pool_size: 20
- max_overflow: 10
- pool_timeout: 30s
- pool_recycle: 1800s
- pool_pre_ping: True

### Docker Configuration

**Services:**
- `db` - PostgreSQL 15 (port 5432, data in `./pgdata`)
- `redis` - Redis 7 (port 6379)
- `backend` - FastAPI app (port 8000, uses `run_production.sh`)
- `frontend` - Nginx serving Vue build (port 5000 → container port 80)

**Volumes:**
- `./backend/uploads` - Uploaded meeting documents
- `./pgdata` - PostgreSQL data persistence

## Important Notes

### CORS Configuration
Backend allows origins: `http://localhost:5173`, `http://127.0.0.1:5173`, `http://localhost:8000`, and `*` (wildcard)

### Static Files
Meeting attachments served at `/static/*` endpoint, mapped to `backend/uploads/` directory

### Port Configuration
- Development backend: 8000 (or 8001 per vite.config.js proxy)
- Development frontend: 5173
- Production frontend: 5000 (mapped to container port 80)

### Multi-Worker Support
When using Gunicorn with multiple workers, Redis is REQUIRED for Socket.IO to work correctly across workers. Without Redis, Socket.IO events will only reach clients connected to the same worker.

### Database Migrations
No migration system currently in place. Schema changes handled by SQLModel's `create_all()` which only creates missing tables. For schema modifications, manual migration or database recreation may be needed.

### Authentication
Simple password-based authentication stored in plain text (demo purposes). User passwords visible in admin interface with "click to show" functionality.

### File Uploads
Attachments stored in `backend/uploads/` with unique filenames. Display names stored separately in database for user-friendly renaming.

### Theme Support
Frontend supports dark mode via `src/dark_theme.css` and `useTheme` composable.
