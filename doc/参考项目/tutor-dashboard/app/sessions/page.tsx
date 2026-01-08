"use client"

import { DashboardLayout } from "@/components/dashboard-layout"
import { SessionsOverview } from "@/components/sessions/sessions-overview"
import { UpcomingSessions } from "@/components/sessions/upcoming-sessions"
import { SessionCalendar } from "@/components/sessions/session-calendar"
import { SessionHistory } from "@/components/sessions/session-history"

export default function SessionsPage() {
  return (
    <DashboardLayout>
      <div className="space-y-6">
        {/* Sessions Overview Cards */}
        <SessionsOverview />

        {/* Calendar and Upcoming Sessions */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          <div className="lg:col-span-2">
            <SessionCalendar />
          </div>
          <div className="lg:col-span-1">
            <UpcomingSessions />
          </div>
        </div>

        {/* Session History */}
        <SessionHistory />
      </div>
    </DashboardLayout>
  )
}
