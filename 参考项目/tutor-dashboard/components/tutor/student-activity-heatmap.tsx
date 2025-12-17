"use client"

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { useState, useMemo } from "react"

const generateHeatmapData = (timeRange: string, selectedMonth?: string) => {
  const data = []

  if (timeRange === "specific-month" && selectedMonth) {
    const [year, month] = selectedMonth.split("-").map(Number)
    const daysInMonth = new Date(year, month, 0).getDate()
    const firstDay = new Date(year, month - 1, 1).getDay() // 0 = Sunday

    // Create calendar grid for the month
    for (let week = 0; week < 6; week++) {
      for (let dayOfWeek = 0; dayOfWeek < 7; dayOfWeek++) {
        const dayNumber = week * 7 + dayOfWeek - firstDay + 1

        if (dayNumber > 0 && dayNumber <= daysInMonth) {
          for (let hour = 0; hour < 24; hour++) {
            let intensity = Math.random() * 0.3

            // Realistic patterns: higher activity during business hours and weekdays
            if (hour >= 9 && hour <= 21) intensity += Math.random() * 0.5
            if (dayOfWeek >= 1 && dayOfWeek <= 5) intensity += Math.random() * 0.4

            data.push({
              date: dayNumber,
              dayOfWeek,
              week,
              hour,
              intensity: Math.min(intensity, 1),
              students: Math.floor(intensity * 75),
            })
          }
        }
      }
    }
  } else {
    const days = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"]

    for (let day = 0; day < 7; day++) {
      for (let hour = 0; hour < 24; hour++) {
        let intensity = Math.random() * 0.3

        if (timeRange === "month") {
          if (hour >= 9 && hour <= 21) intensity += Math.random() * 0.5
          if (day >= 1 && day <= 5) intensity += Math.random() * 0.4
          intensity *= 1.2
        } else if (timeRange === "quarter") {
          if (hour >= 10 && hour <= 20) intensity += Math.random() * 0.6
          if (day >= 1 && day <= 5) intensity += Math.random() * 0.5
          intensity *= 1.4
        } else {
          if (hour >= 8 && hour <= 22) intensity += Math.random() * 0.4
          if (day >= 1 && day <= 5) intensity += Math.random() * 0.3
        }

        data.push({
          day: days[day],
          hour,
          intensity: Math.min(intensity, 1),
          students: Math.floor(intensity * (timeRange === "quarter" ? 100 : timeRange === "month" ? 75 : 50)),
        })
      }
    }
  }

  return data
}

export function StudentActivityHeatmap() {
  const [timeRange, setTimeRange] = useState("week")
  const [selectedMonth, setSelectedMonth] = useState("2024-12") // Added month selection state

  const data = useMemo(() => generateHeatmapData(timeRange, selectedMonth), [timeRange, selectedMonth])

  const getIntensityColor = (intensity: number) => {
    if (intensity < 0.2) return "bg-gray-100 dark:bg-gray-800"
    if (intensity < 0.4) return "bg-teal-200 dark:bg-teal-900"
    if (intensity < 0.6) return "bg-teal-400 dark:bg-teal-700"
    if (intensity < 0.8) return "bg-teal-600 dark:bg-teal-500"
    return "bg-teal-800 dark:bg-teal-300"
  }

  const hours = Array.from({ length: 24 }, (_, i) => i)
  const days = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"]

  const getTimeRangeDescription = () => {
    switch (timeRange) {
      case "specific-month":
        const monthName = new Date(selectedMonth + "-01").toLocaleDateString("en-US", {
          month: "long",
          year: "numeric",
        })
        return `Student activity patterns for ${monthName}`
      case "month":
        return "Average student activity patterns over the past month"
      case "quarter":
        return "Quarterly trends showing seasonal learning patterns"
      default:
        return "Visual representation of when students are most active this week"
    }
  }

  const getMonthOptions = () => {
    const options = []
    const currentDate = new Date()
    for (let i = 0; i < 12; i++) {
      const date = new Date(currentDate.getFullYear(), currentDate.getMonth() - i, 1)
      const value = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}`
      const label = date.toLocaleDateString("en-US", { month: "long", year: "numeric" })
      options.push({ value, label })
    }
    return options
  }

  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between">
        <div>
          <CardTitle>Student Activity Heat Map</CardTitle>
          <CardDescription>{getTimeRangeDescription()}</CardDescription>
        </div>
        <div className="flex gap-2">
          <Select value={timeRange} onValueChange={setTimeRange}>
            <SelectTrigger className="w-32">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="week">This Week</SelectItem>
              <SelectItem value="month">This Month</SelectItem>
              <SelectItem value="quarter">This Quarter</SelectItem>
              <SelectItem value="specific-month">Specific Month</SelectItem>
            </SelectContent>
          </Select>

          {timeRange === "specific-month" && (
            <Select value={selectedMonth} onValueChange={setSelectedMonth}>
              <SelectTrigger className="w-40">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {getMonthOptions().map((option) => (
                  <SelectItem key={option.value} value={option.value}>
                    {option.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          )}
        </div>
      </CardHeader>
      <CardContent>
        <div className="space-y-4">
          {/* Heat Map Grid */}
          <div className="overflow-x-auto">
            <div className="min-w-[800px]">
              {/* Hour labels */}
              <div className="flex mb-2">
                <div className="w-12"></div>
                {hours.map((hour) => (
                  <div key={hour} className="flex-1 text-xs text-center text-muted-foreground min-w-[30px]">
                    {hour === 0 ? "12a" : hour <= 12 ? `${hour}a` : `${hour - 12}p`}
                  </div>
                ))}
              </div>

              {timeRange === "specific-month" ? (
                // Calendar view for specific month
                <>
                  {/* Day labels for calendar */}
                  <div className="flex mb-2">
                    <div className="w-12"></div>
                    {days.map((day) => (
                      <div key={day} className="flex-1 text-xs text-center text-muted-foreground min-w-[30px]">
                        {day}
                      </div>
                    ))}
                  </div>

                  {/* Calendar grid showing dates */}
                  {Array.from({ length: 6 }, (_, week) => {
                    const [year, month] = selectedMonth.split("-").map(Number)
                    const firstDay = new Date(year, month - 1, 1).getDay()
                    const daysInMonth = new Date(year, month, 0).getDate()

                    return (
                      <div key={week} className="flex items-center mb-1">
                        <div className="w-12 text-sm font-medium text-muted-foreground">W{week + 1}</div>
                        {Array.from({ length: 7 }, (_, dayOfWeek) => {
                          const dayNumber = week * 7 + dayOfWeek - firstDay + 1
                          const isValidDay = dayNumber > 0 && dayNumber <= daysInMonth

                          if (!isValidDay) {
                            return <div key={dayOfWeek} className="flex-1 min-w-[30px] mx-0.5"></div>
                          }

                          // Calculate average intensity for this day
                          const dayData = data.filter((d) => d.date === dayNumber)
                          const avgIntensity = dayData.reduce((sum, d) => sum + d.intensity, 0) / dayData.length

                          return (
                            <div
                              key={dayOfWeek}
                              className={`flex-1 h-6 min-w-[30px] mx-0.5 rounded-sm cursor-pointer transition-all hover:scale-110 flex items-center justify-center text-xs font-medium ${getIntensityColor(avgIntensity)}`}
                              title={`${dayNumber} - Average activity: ${Math.round(avgIntensity * 100)}%`}
                            >
                              {dayNumber}
                            </div>
                          )
                        })}
                      </div>
                    )
                  })}
                </>
              ) : (
                // Original weekly view
                days.map((day) => (
                  <div key={day} className="flex items-center mb-1">
                    <div className="w-12 text-sm font-medium text-muted-foreground">{day}</div>
                    {hours.map((hour) => {
                      const cellData = data.find((d) => d.day === day && d.hour === hour)
                      return (
                        <div
                          key={`${day}-${hour}`}
                          className={`flex-1 h-6 min-w-[30px] mx-0.5 rounded-sm cursor-pointer transition-all hover:scale-110 ${getIntensityColor(cellData?.intensity || 0)}`}
                          title={`${day} ${hour}:00 - ${cellData?.students || 0} active students (${timeRange})`}
                        />
                      )
                    })}
                  </div>
                ))
              )}
            </div>
          </div>

          {/* Legend */}
          <div className="flex items-center justify-between text-sm text-muted-foreground">
            <span>Less active</span>
            <div className="flex items-center space-x-1">
              <div className="w-3 h-3 bg-gray-100 dark:bg-gray-800 rounded-sm"></div>
              <div className="w-3 h-3 bg-teal-200 dark:bg-teal-900 rounded-sm"></div>
              <div className="w-3 h-3 bg-teal-400 dark:bg-teal-700 rounded-sm"></div>
              <div className="w-3 h-3 bg-teal-600 dark:bg-teal-500 rounded-sm"></div>
              <div className="w-3 h-3 bg-teal-800 dark:bg-teal-300 rounded-sm"></div>
            </div>
            <span>More active</span>
          </div>
        </div>
      </CardContent>
    </Card>
  )
}
