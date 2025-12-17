"use client"

import { Card, CardContent } from "@/components/ui/card"
import { MessageCircle, Users, Bot, Clock } from "lucide-react"

const overviewData = [
  {
    title: "Unread Messages",
    value: "12",
    subtitle: "From 8 students",
    icon: MessageCircle,
    color: "text-blue-600",
    bgColor: "bg-blue-100 dark:bg-blue-900/20",
  },
  {
    title: "Active Conversations",
    value: "24",
    subtitle: "This week",
    icon: Users,
    color: "text-green-600",
    bgColor: "bg-green-100 dark:bg-green-900/20",
  },
  {
    title: "AI Assistance",
    value: "89",
    subtitle: "Queries resolved",
    icon: Bot,
    color: "text-purple-600",
    bgColor: "bg-purple-100 dark:bg-purple-900/20",
  },
  {
    title: "Avg Response Time",
    value: "2.3h",
    subtitle: "Last 7 days",
    icon: Clock,
    color: "text-orange-600",
    bgColor: "bg-orange-100 dark:bg-orange-900/20",
  },
  {
    title: "Teaching Assistance",
    value: "89",
    subtitle: "Queries resolved",
    icon: Bot,
    color: "text-purple-600",
    bgColor: "bg-purple-100 dark:bg-purple-900/20",
  },
]

export function MessagesOverview() {
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
