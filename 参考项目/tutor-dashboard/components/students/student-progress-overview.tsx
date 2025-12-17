"use client"

import { Card, CardContent } from "@/components/ui/card"
import { Users, TrendingUp, Clock, Award } from "lucide-react"

const overviewData = [
  {
    title: "Total Students",
    value: "248",
    subtitle: "+12 this month",
    icon: Users,
    color: "text-blue-600",
    bgColor: "bg-blue-100 dark:bg-blue-900/20",
  },
  {
    title: "Active This Week",
    value: "186",
    subtitle: "75% engagement rate",
    icon: TrendingUp,
    color: "text-green-600",
    bgColor: "bg-green-100 dark:bg-green-900/20",
  },
  {
    title: "Avg. Study Time",
    value: "4.2h",
    subtitle: "Per student/week",
    icon: Clock,
    color: "text-orange-600",
    bgColor: "bg-orange-100 dark:bg-orange-900/20",
  },
  {
    title: "Completed Courses",
    value: "89",
    subtitle: "This month",
    icon: Award,
    color: "text-purple-600",
    bgColor: "bg-purple-100 dark:bg-purple-900/20",
  },
]

export function StudentProgressOverview() {
  return (
    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
      {overviewData.map((item, index) => (
        <Card key={index} className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700">
          <CardContent className="p-6">
            <div className="flex items-center justify-between mb-4">
              <div className={`p-3 rounded-lg ${item.bgColor}`}>
                <item.icon className={`h-6 w-6 ${item.color}`} />
              </div>
            </div>
            <div className="space-y-2">
              <p className="text-sm font-medium text-gray-500 dark:text-gray-400">{item.title}</p>
              <p className="text-3xl font-bold text-gray-900 dark:text-white">{item.value}</p>
              <p className="text-sm text-gray-500 dark:text-gray-400">{item.subtitle}</p>
            </div>
          </CardContent>
        </Card>
      ))}
    </div>
  )
}
