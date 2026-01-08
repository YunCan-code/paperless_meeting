"use client"

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { TrendingUp, TrendingDown, Minus } from "lucide-react"

// Mock data for course popularity
const courseData = [
  { name: "HTML & CSS Essentials", category: "Web Basics", popularity: 0.95, trend: "up", students: 2340 },
  { name: "Modern JavaScript", category: "Programming", popularity: 0.88, trend: "up", students: 3120 },
  { name: "React for Beginners", category: "Frontend", popularity: 0.82, trend: "stable", students: 1980 },
  { name: "Python Fundamentals", category: "Programming", popularity: 0.75, trend: "down", students: 1456 },
  { name: "Node.js Backend", category: "Backend", popularity: 0.71, trend: "up", students: 1280 },
  { name: "Vue.js Complete Guide", category: "Frontend", popularity: 0.68, trend: "up", students: 1120 },
  { name: "Database Design", category: "Backend", popularity: 0.64, trend: "stable", students: 980 },
  { name: "Mobile Development", category: "Mobile", popularity: 0.59, trend: "down", students: 870 },
  { name: "DevOps Essentials", category: "Infrastructure", popularity: 0.52, trend: "up", students: 760 },
  { name: "UI/UX Design", category: "Design", popularity: 0.48, trend: "stable", students: 650 },
  { name: "Cybersecurity Basics", category: "Security", popularity: 0.43, trend: "up", students: 540 },
  { name: "Cloud Computing", category: "Infrastructure", popularity: 0.38, trend: "down", students: 430 },
]

const categories = [
  "Programming",
  "Design",
  "Computer Science",
  "AI/ML",
  "Backend",
  "Infrastructure",
  "Security",
  "Web Basics",
  "Frontend",
  "Mobile",
]

export function CoursePopularityHeatmap() {
  const getPopularityColor = (popularity: number) => {
    if (popularity < 0.2) return "bg-gray-100 dark:bg-gray-800"
    if (popularity < 0.4) return "bg-blue-200 dark:bg-blue-900"
    if (popularity < 0.6) return "bg-blue-400 dark:bg-blue-700"
    if (popularity < 0.8) return "bg-blue-600 dark:bg-blue-500"
    return "bg-blue-800 dark:bg-blue-300"
  }

  const getTrendIcon = (trend: string) => {
    switch (trend) {
      case "up":
        return <TrendingUp className="h-3 w-3 text-green-500" />
      case "down":
        return <TrendingDown className="h-3 w-3 text-red-500" />
      default:
        return <Minus className="h-3 w-3 text-gray-500" />
    }
  }

  const getCategoryColor = (category: string) => {
    const colors = {
      Programming: "bg-purple-100 text-purple-800 dark:bg-purple-900 dark:text-purple-200",
      Design: "bg-pink-100 text-pink-800 dark:bg-pink-900 dark:text-pink-200",
      "Computer Science": "bg-indigo-100 text-indigo-800 dark:bg-indigo-900 dark:text-indigo-200",
      "AI/ML": "bg-emerald-100 text-emerald-800 dark:bg-emerald-900 dark:text-emerald-200",
      Backend: "bg-orange-100 text-orange-800 dark:bg-orange-900 dark:text-orange-200",
      Infrastructure: "bg-cyan-100 text-cyan-800 dark:bg-cyan-900 dark:text-cyan-200",
      Security: "bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200",
      "Web Basics": "bg-teal-100 text-teal-800 dark:bg-teal-900 dark:text-teal-200",
      Frontend: "bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200",
      Mobile: "bg-yellow-100 text-yellow-800 dark:bg-yellow-900 dark:text-yellow-200",
    }
    return colors[category as keyof typeof colors] || "bg-gray-100 text-gray-800"
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Course Popularity Heat Map</CardTitle>
        <CardDescription>Visual representation of course engagement and popularity trends</CardDescription>
      </CardHeader>
      <CardContent>
        <div className="space-y-4">
          {/* Heat Map Grid */}
          <div className="grid gap-2">
            {courseData.map((course, index) => (
              <div
                key={course.name}
                className="flex items-center justify-between p-3 rounded-lg border hover:shadow-sm transition-all"
              >
                <div className="flex items-center space-x-3 flex-1">
                  {/* Popularity Heat Indicator */}
                  <div
                    className={`w-4 h-4 rounded-sm ${getPopularityColor(course.popularity)}`}
                    title={`Popularity: ${Math.round(course.popularity * 100)}%`}
                  />

                  {/* Course Info */}
                  <div className="flex-1">
                    <div className="flex items-center space-x-2">
                      <span className="font-medium">{course.name}</span>
                      {getTrendIcon(course.trend)}
                    </div>
                    <div className="flex items-center space-x-2 mt-1">
                      <Badge variant="secondary" className={getCategoryColor(course.category)}>
                        {course.category}
                      </Badge>
                      <span className="text-sm text-muted-foreground">{course.students.toLocaleString()} students</span>
                    </div>
                  </div>
                </div>

                {/* Popularity Bar */}
                <div className="flex items-center space-x-2">
                  <div className="w-20 h-2 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden">
                    <div
                      className="h-full bg-blue-500 transition-all duration-300"
                      style={{ width: `${course.popularity * 100}%` }}
                    />
                  </div>
                  <span className="text-sm font-medium w-10 text-right">{Math.round(course.popularity * 100)}%</span>
                </div>
              </div>
            ))}
          </div>

          {/* Legend */}
          <div className="flex items-center justify-between text-sm text-muted-foreground pt-4 border-t">
            <div className="flex items-center space-x-4">
              <span>Heat Intensity:</span>
              <div className="flex items-center space-x-1">
                <div className="w-3 h-3 bg-gray-100 dark:bg-gray-800 rounded-sm"></div>
                <div className="w-3 h-3 bg-blue-200 dark:bg-blue-900 rounded-sm"></div>
                <div className="w-3 h-3 bg-blue-400 dark:bg-blue-700 rounded-sm"></div>
                <div className="w-3 h-3 bg-blue-600 dark:bg-blue-500 rounded-sm"></div>
                <div className="w-3 h-3 bg-blue-800 dark:bg-blue-300 rounded-sm"></div>
              </div>
            </div>
            <div className="flex items-center space-x-4">
              <div className="flex items-center space-x-1">
                <TrendingUp className="h-3 w-3 text-green-500" />
                <span>Rising</span>
              </div>
              <div className="flex items-center space-x-1">
                <Minus className="h-3 w-3 text-gray-500" />
                <span>Stable</span>
              </div>
              <div className="flex items-center space-x-1">
                <TrendingDown className="h-3 w-3 text-red-500" />
                <span>Declining</span>
              </div>
            </div>
          </div>
        </div>
      </CardContent>
    </Card>
  )
}
