"use client"

import { useState } from "react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { ChevronLeft, ChevronRight, Plus } from "lucide-react"
import { VideoSessionModal } from "./video-session-modal"

const daysOfWeek = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"]
const currentDate = new Date()
const currentMonth = currentDate.getMonth()
const currentYear = currentDate.getFullYear()

// Mock session data for calendar
const sessionDates = [5, 8, 12, 15, 18, 22, 25, 28]

export function SessionCalendar() {
  const [selectedDate, setSelectedDate] = useState(currentDate.getDate())
  const [viewMonth, setViewMonth] = useState(currentMonth)
  const [viewYear, setViewYear] = useState(currentYear)

  const getDaysInMonth = (month: number, year: number) => {
    return new Date(year, month + 1, 0).getDate()
  }

  const getFirstDayOfMonth = (month: number, year: number) => {
    return new Date(year, month, 1).getDay()
  }

  const navigateMonth = (direction: "prev" | "next") => {
    if (direction === "prev") {
      if (viewMonth === 0) {
        setViewMonth(11)
        setViewYear(viewYear - 1)
      } else {
        setViewMonth(viewMonth - 1)
      }
    } else {
      if (viewMonth === 11) {
        setViewMonth(0)
        setViewYear(viewYear + 1)
      } else {
        setViewMonth(viewMonth + 1)
      }
    }
  }

  const daysInMonth = getDaysInMonth(viewMonth, viewYear)
  const firstDayOfMonth = getFirstDayOfMonth(viewMonth, viewYear)
  const monthName = new Date(viewYear, viewMonth).toLocaleString("default", { month: "long" })

  const calendarDays = []

  // Empty cells for days before the first day of the month
  for (let i = 0; i < firstDayOfMonth; i++) {
    calendarDays.push(null)
  }

  // Days of the month
  for (let day = 1; day <= daysInMonth; day++) {
    calendarDays.push(day)
  }

  return (
    <Card className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700">
      <CardHeader>
        <div className="flex items-center justify-between">
          <CardTitle className="text-xl font-semibold text-gray-900 dark:text-white">Session Calendar</CardTitle>
          <VideoSessionModal
            trigger={
              <Button size="sm" className="bg-teal-600 hover:bg-teal-700">
                <Plus className="h-4 w-4 mr-2" />
                Add Session
              </Button>
            }
          />
        </div>
        <div className="flex items-center justify-between mt-4">
          <h3 className="text-lg font-semibold text-gray-900 dark:text-white">
            {monthName} {viewYear}
          </h3>
          <div className="flex items-center gap-2">
            <Button variant="outline" size="sm" onClick={() => navigateMonth("prev")}>
              <ChevronLeft className="h-4 w-4" />
            </Button>
            <Button variant="outline" size="sm" onClick={() => navigateMonth("next")}>
              <ChevronRight className="h-4 w-4" />
            </Button>
          </div>
        </div>
      </CardHeader>
      <CardContent>
        <div className="grid grid-cols-7 gap-1 mb-4">
          {daysOfWeek.map((day) => (
            <div key={day} className="p-2 text-center text-sm font-medium text-gray-500 dark:text-gray-400">
              {day}
            </div>
          ))}
        </div>
        <div className="grid grid-cols-7 gap-1">
          {calendarDays.map((day, index) => (
            <div key={index} className="aspect-square">
              {day && (
                <button
                  onClick={() => setSelectedDate(day)}
                  className={`w-full h-full flex items-center justify-center text-sm rounded-lg transition-colors relative ${
                    day === selectedDate
                      ? "bg-teal-600 text-white"
                      : sessionDates.includes(day)
                        ? "bg-blue-100 text-blue-800 dark:bg-blue-900/20 dark:text-blue-400 hover:bg-blue-200 dark:hover:bg-blue-900/40"
                        : "hover:bg-gray-100 dark:hover:bg-gray-700 text-gray-900 dark:text-white"
                  }`}
                >
                  {day}
                  {sessionDates.includes(day) && (
                    <div className="absolute bottom-1 left-1/2 transform -translate-x-1/2 w-1 h-1 bg-current rounded-full"></div>
                  )}
                </button>
              )}
            </div>
          ))}
        </div>
      </CardContent>
    </Card>
  )
}
