"use client"

import { DashboardLayout } from "@/components/dashboard-layout"
import { KpiCards } from "@/components/tutor/kpi-cards"
import { EngagementHeatOverview } from "@/components/tutor/engagement-heat-overview"
import { StudentActivityHeatmap } from "@/components/tutor/student-activity-heatmap"
import { CoursePopularityHeatmap } from "@/components/tutor/course-popularity-heatmap"
import { CourseWatchTimeChart } from "@/components/tutor/course-watch-time-chart"
import { StudentsByCategoryChart } from "@/components/tutor/students-by-category-chart"
import { StudentsByLocation } from "@/components/tutor/students-by-location"
import { TopCoursesTable } from "@/components/tutor/top-courses-table"

export default function DashboardPage() {
  return (
    <DashboardLayout>
      <div className="space-y-6">
        {/* KPI Cards - Full Width */}
        <KpiCards />

        {/* Engagement Heat Overview */}
        <EngagementHeatOverview />

        {/* Student Activity Heat Map - Full Width */}
        <StudentActivityHeatmap />

        {/* Course Watch Time Chart - Full Width */}
        <CourseWatchTimeChart />

        {/* Heat Maps and Charts - Side by Side */}
        <div className="grid grid-cols-1 xl:grid-cols-2 gap-6">
          <CoursePopularityHeatmap />
          <div className="space-y-6">
            <StudentsByCategoryChart />
            <StudentsByLocation />
          </div>
        </div>

        {/* Top Courses Table - Full Width */}
        <TopCoursesTable />

        {/* Footer */}
        <footer className="text-center py-4 text-gray-500 dark:text-gray-400 text-sm">
          © {new Date().getFullYear()} XLearn • Tutor Dashboard
        </footer>
      </div>
    </DashboardLayout>
  )
}
