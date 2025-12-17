"use client"

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Calendar, Clock, Video, Phone } from "lucide-react"
import { VideoSessionModal } from "./video-session-modal"

const upcomingSessions = [
  {
    id: 1,
    student: "Alice Johnson",
    course: "React Fundamentals",
    time: "2:00 PM",
    duration: "1 hour",
    type: "video",
    status: "confirmed",
    avatar: "https://i.pravatar.cc/60?img=1",
  },
  {
    id: 2,
    student: "Bob Smith",
    course: "JavaScript Basics",
    time: "4:00 PM",
    duration: "45 mins",
    type: "phone",
    status: "pending",
    avatar: "https://i.pravatar.cc/60?img=2",
  },
  {
    id: 3,
    student: "Carol Davis",
    course: "CSS Advanced",
    time: "6:00 PM",
    duration: "1.5 hours",
    type: "video",
    status: "confirmed",
    avatar: "https://i.pravatar.cc/60?img=3",
  },
  {
    id: 4,
    student: "David Wilson",
    course: "HTML Basics",
    time: "8:00 PM",
    duration: "1 hour",
    type: "video",
    status: "confirmed",
    avatar: "https://i.pravatar.cc/60?img=4",
  },
]

export function UpcomingSessions() {
  const getStatusColor = (status: string) => {
    switch (status) {
      case "confirmed":
        return "bg-green-100 text-green-800 dark:bg-green-900/20 dark:text-green-400"
      case "pending":
        return "bg-yellow-100 text-yellow-800 dark:bg-yellow-900/20 dark:text-yellow-400"
      case "cancelled":
        return "bg-red-100 text-red-800 dark:bg-red-900/20 dark:text-red-400"
      default:
        return "bg-gray-100 text-gray-800 dark:bg-gray-900/20 dark:text-gray-400"
    }
  }

  return (
    <Card className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700">
      <CardHeader>
        <div className="flex items-center justify-between">
          <CardTitle className="text-xl font-semibold text-gray-900 dark:text-white">Today's Sessions</CardTitle>
          <VideoSessionModal
            trigger={
              <Button size="sm" className="bg-teal-600 hover:bg-teal-700">
                <Calendar className="h-4 w-4 mr-2" />
                Schedule New
              </Button>
            }
          />
        </div>
      </CardHeader>
      <CardContent>
        <div className="space-y-4">
          {upcomingSessions.map((session) => (
            <div
              key={session.id}
              className="flex items-center justify-between p-4 border border-gray-200 dark:border-gray-700 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors"
            >
              <div className="flex items-center gap-3">
                <img
                  src={session.avatar || "/placeholder.svg"}
                  alt={session.student}
                  className="w-10 h-10 rounded-full object-cover"
                />
                <div className="space-y-1">
                  <div className="flex items-center gap-2">
                    <h3 className="font-semibold text-gray-900 dark:text-white text-sm">{session.student}</h3>
                    <Badge className={getStatusColor(session.status)}>{session.status}</Badge>
                  </div>
                  <p className="text-sm text-gray-600 dark:text-gray-300">{session.course}</p>
                  <div className="flex items-center gap-4 text-xs text-gray-500 dark:text-gray-400">
                    <div className="flex items-center gap-1">
                      <Clock className="h-3 w-3" />
                      <span>{session.time}</span>
                    </div>
                    <div className="flex items-center gap-1">
                      {session.type === "video" ? <Video className="h-3 w-3" /> : <Phone className="h-3 w-3" />}
                      <span>{session.duration}</span>
                    </div>
                  </div>
                </div>
              </div>

              <div className="flex items-center gap-2">
                <Button size="sm" variant="outline">
                  Reschedule
                </Button>
                <Button size="sm" className="bg-teal-600 hover:bg-teal-700">
                  Join
                </Button>
              </div>
            </div>
          ))}
        </div>
      </CardContent>
    </Card>
  )
}
