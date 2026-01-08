"use client"

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Clock, CheckCircle, PlayCircle, BookOpen } from "lucide-react"

const activityData = [
  {
    id: 1,
    student: "Alice Johnson",
    action: "Completed lesson",
    course: "React Hooks",
    time: "2 hours ago",
    type: "completion",
    icon: CheckCircle,
  },
  {
    id: 2,
    student: "Bob Smith",
    action: "Started watching",
    course: "JavaScript Arrays",
    time: "4 hours ago",
    type: "started",
    icon: PlayCircle,
  },
  {
    id: 3,
    student: "Eva Brown",
    action: "Submitted assignment",
    course: "CSS Flexbox",
    time: "6 hours ago",
    type: "submission",
    icon: BookOpen,
  },
  {
    id: 4,
    student: "Carol Davis",
    action: "Completed course",
    course: "HTML Basics",
    time: "1 day ago",
    type: "completion",
    icon: CheckCircle,
  },
  {
    id: 5,
    student: "David Wilson",
    action: "Started course",
    course: "Modern JavaScript",
    time: "2 days ago",
    type: "started",
    icon: PlayCircle,
  },
]

export function RecentActivity() {
  const getActivityColor = (type: string) => {
    switch (type) {
      case "completion":
        return "text-green-600 bg-green-100 dark:bg-green-900/20"
      case "started":
        return "text-blue-600 bg-blue-100 dark:bg-blue-900/20"
      case "submission":
        return "text-purple-600 bg-purple-100 dark:bg-purple-900/20"
      default:
        return "text-gray-600 bg-gray-100 dark:bg-gray-900/20"
    }
  }

  return (
    <Card className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700">
      <CardHeader>
        <CardTitle className="text-xl font-semibold text-gray-900 dark:text-white">Recent Activity</CardTitle>
      </CardHeader>
      <CardContent>
        <div className="space-y-4">
          {activityData.map((activity) => (
            <div
              key={activity.id}
              className="flex items-start gap-3 p-3 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors"
            >
              <div className={`p-2 rounded-full ${getActivityColor(activity.type)}`}>
                <activity.icon className="h-4 w-4" />
              </div>
              <div className="flex-1 space-y-1">
                <div className="flex items-center justify-between">
                  <p className="font-medium text-gray-900 dark:text-white text-sm">{activity.student}</p>
                  <div className="flex items-center gap-1 text-gray-500 dark:text-gray-400">
                    <Clock className="h-3 w-3" />
                    <span className="text-xs">{activity.time}</span>
                  </div>
                </div>
                <p className="text-sm text-gray-600 dark:text-gray-300">{activity.action}</p>
                <Badge variant="secondary" className="text-xs">
                  {activity.course}
                </Badge>
              </div>
            </div>
          ))}
        </div>
      </CardContent>
    </Card>
  )
}
