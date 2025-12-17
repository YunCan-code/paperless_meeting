"use client"

import { DashboardLayout } from "@/components/dashboard-layout"
import { StudentsList } from "@/components/students/students-list"
import { StudentProgressOverview } from "@/components/students/student-progress-overview"
import { RecentActivity } from "@/components/students/recent-activity"

export default function StudentsPage() {
  return (
    <DashboardLayout>
      <div className="space-y-6">
        {/* Progress Overview Cards */}
        <StudentProgressOverview />

        {/* Students List and Recent Activity */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          <div className="lg:col-span-2">
            <StudentsList />
          </div>
          <div className="lg:col-span-1">
            <RecentActivity />
          </div>
        </div>
      </div>
    </DashboardLayout>
  )
}
