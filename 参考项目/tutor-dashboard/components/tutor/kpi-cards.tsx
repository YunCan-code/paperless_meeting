"use client"

import { Card, CardContent } from "@/components/ui/card"
import { TrendingUp, Users, Eye, IndianRupee } from "lucide-react"

const kpiData = [
  {
    title: "Total Students",
    value: "12,480",
    subtitle: "+2.4% vs last week",
    icon: Users,
    trend: "up",
  },
  {
    title: "Active Learners",
    value: "1,136",
    subtitle: "Live & watching now",
    icon: TrendingUp,
    trend: "neutral",
  },
  {
    title: "Course Views",
    value: "567,899",
    subtitle: "All courses (period)",
    icon: Eye,
    trend: "neutral",
  },
  {
    title: "Earnings",
    value: "₹34,650",
    subtitle: "After platform fee",
    icon: IndianRupee,
    trend: "up",
  },
]

export function KpiCards() {
  return (
    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
      {kpiData.map((kpi, index) => (
        <Card key={index} className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 shadow-sm">
          <CardContent className="p-6">
            <div className="flex items-center justify-between mb-4">
              <div className="p-2 bg-gray-100 dark:bg-gray-700 rounded-lg">
                <kpi.icon className="h-5 w-5 text-gray-600 dark:text-gray-400" />
              </div>
            </div>
            <div className="space-y-2">
              <p className="text-sm font-medium text-gray-500 dark:text-gray-400">{kpi.title}</p>
              <p className="text-2xl font-bold text-gray-900 dark:text-white">{kpi.value}</p>
              <p className="text-sm text-gray-500 dark:text-gray-400">{kpi.subtitle}</p>
            </div>
          </CardContent>
        </Card>
      ))}
    </div>
  )
}
