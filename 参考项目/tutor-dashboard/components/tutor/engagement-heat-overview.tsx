"use client"

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Flame, TrendingUp, Users, Clock } from "lucide-react"

// Mock data for engagement metrics
const engagementData = {
  overallHeat: 0.78,
  peakHours: "2:00 PM - 4:00 PM",
  hottestDay: "Wednesday",
  activeStudents: 342,
  avgSessionTime: "45 min",
  heatTrend: "up",
}

const hotTopics = [
  { topic: "React Hooks", heat: 0.95, students: 89 },
  { topic: "JavaScript ES6", heat: 0.87, students: 76 },
  { topic: "CSS Grid", heat: 0.82, students: 64 },
  { topic: "Node.js APIs", heat: 0.74, students: 52 },
  { topic: "Database Queries", heat: 0.68, students: 43 },
]

export function EngagementHeatOverview() {
  const getHeatLevel = (heat: number) => {
    if (heat >= 0.8) return { label: "🔥 Hot", color: "bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200" }
    if (heat >= 0.6)
      return { label: "🌡️ Warm", color: "bg-orange-100 text-orange-800 dark:bg-orange-900 dark:text-orange-200" }
    if (heat >= 0.4) return { label: "😐 Cool", color: "bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-200" }
    return { label: "🧊 Cold", color: "bg-gray-100 text-gray-800 dark:bg-gray-900 dark:text-gray-200" }
  }

  const overallHeatLevel = getHeatLevel(engagementData.overallHeat)

  return (
    <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
      {/* Overall Heat Score */}
      <Card>
        <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
          <CardTitle className="text-sm font-medium">Overall Heat Score</CardTitle>
          <Flame className="h-4 w-4 text-orange-500" />
        </CardHeader>
        <CardContent>
          <div className="text-2xl font-bold">{Math.round(engagementData.overallHeat * 100)}%</div>
          <div className="flex items-center space-x-2 mt-2">
            <Badge className={overallHeatLevel.color}>{overallHeatLevel.label}</Badge>
            <TrendingUp className="h-3 w-3 text-green-500" />
          </div>
          <p className="text-xs text-muted-foreground mt-2">Peak: {engagementData.peakHours}</p>
        </CardContent>
      </Card>

      {/* Active Students */}
      <Card>
        <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
          <CardTitle className="text-sm font-medium">Active Students</CardTitle>
          <Users className="h-4 w-4 text-blue-500" />
        </CardHeader>
        <CardContent>
          <div className="text-2xl font-bold">{engagementData.activeStudents}</div>
          <p className="text-xs text-muted-foreground">Hottest day: {engagementData.hottestDay}</p>
          <div className="mt-2">
            <div className="w-full bg-gray-200 dark:bg-gray-700 rounded-full h-2">
              <div
                className="bg-blue-500 h-2 rounded-full transition-all duration-300"
                style={{ width: `${(engagementData.activeStudents / 500) * 100}%` }}
              />
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Average Session Time */}
      <Card>
        <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
          <CardTitle className="text-sm font-medium">Avg Session Time</CardTitle>
          <Clock className="h-4 w-4 text-green-500" />
        </CardHeader>
        <CardContent>
          <div className="text-2xl font-bold">{engagementData.avgSessionTime}</div>
          <p className="text-xs text-muted-foreground">+12% from last week</p>
          <div className="mt-2">
            <div className="w-full bg-gray-200 dark:bg-gray-700 rounded-full h-2">
              <div className="bg-green-500 h-2 rounded-full transition-all duration-300" style={{ width: "75%" }} />
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Hot Topics */}
      <Card className="lg:col-span-3">
        <CardHeader>
          <CardTitle>🔥 Hot Topics Right Now</CardTitle>
          <CardDescription>Most engaging topics based on student activity and interaction</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="space-y-3">
            {hotTopics.map((topic, index) => (
              <div key={topic.topic} className="flex items-center justify-between p-3 rounded-lg bg-muted/50">
                <div className="flex items-center space-x-3">
                  <div className="flex items-center justify-center w-6 h-6 rounded-full bg-orange-100 dark:bg-orange-900 text-orange-600 dark:text-orange-400 text-sm font-bold">
                    {index + 1}
                  </div>
                  <span className="font-medium">{topic.topic}</span>
                  <Badge className={getHeatLevel(topic.heat).color}>{getHeatLevel(topic.heat).label}</Badge>
                </div>
                <div className="flex items-center space-x-4">
                  <span className="text-sm text-muted-foreground">{topic.students} students</span>
                  <div className="flex items-center space-x-2">
                    <div className="w-16 h-2 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden">
                      <div
                        className="h-full bg-gradient-to-r from-orange-400 to-red-500 transition-all duration-300"
                        style={{ width: `${topic.heat * 100}%` }}
                      />
                    </div>
                    <span className="text-sm font-medium w-8 text-right">{Math.round(topic.heat * 100)}%</span>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
