"use client"

import { useState } from "react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Search, Filter, Download, Star } from "lucide-react"
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from "@/components/ui/dropdown-menu"

const sessionHistory = [
  {
    id: 1,
    student: "Alice Johnson",
    course: "React Fundamentals",
    date: "2024-01-15",
    duration: "1h 15m",
    status: "completed",
    rating: 5,
    notes: "Great progress on hooks concept",
  },
  {
    id: 2,
    student: "Bob Smith",
    course: "JavaScript Basics",
    date: "2024-01-14",
    duration: "45m",
    status: "completed",
    rating: 4,
    notes: "Needs more practice with arrays",
  },
  {
    id: 3,
    student: "Carol Davis",
    course: "CSS Advanced",
    date: "2024-01-13",
    duration: "1h 30m",
    status: "completed",
    rating: 5,
    notes: "Excellent understanding of flexbox",
  },
  {
    id: 4,
    student: "David Wilson",
    course: "HTML Basics",
    date: "2024-01-12",
    duration: "1h",
    status: "cancelled",
    rating: null,
    notes: "Student cancelled due to illness",
  },
  {
    id: 5,
    student: "Eva Brown",
    course: "React Hooks",
    date: "2024-01-11",
    duration: "1h 20m",
    status: "completed",
    rating: 4,
    notes: "Good progress, homework assigned",
  },
]

export function SessionHistory() {
  const [searchTerm, setSearchTerm] = useState("")
  const [selectedStatus, setSelectedStatus] = useState("all")

  const filteredSessions = sessionHistory.filter((session) => {
    const matchesSearch =
      session.student.toLowerCase().includes(searchTerm.toLowerCase()) ||
      session.course.toLowerCase().includes(searchTerm.toLowerCase())
    const matchesStatus = selectedStatus === "all" || session.status === selectedStatus
    return matchesSearch && matchesStatus
  })

  const getStatusColor = (status: string) => {
    switch (status) {
      case "completed":
        return "bg-green-100 text-green-800 dark:bg-green-900/20 dark:text-green-400"
      case "cancelled":
        return "bg-red-100 text-red-800 dark:bg-red-900/20 dark:text-red-400"
      case "no-show":
        return "bg-gray-100 text-gray-800 dark:bg-gray-900/20 dark:text-gray-400"
      default:
        return "bg-gray-100 text-gray-800 dark:bg-gray-900/20 dark:text-gray-400"
    }
  }

  const renderStars = (rating: number | null) => {
    if (!rating) return <span className="text-gray-400">N/A</span>

    return (
      <div className="flex items-center gap-1">
        {[1, 2, 3, 4, 5].map((star) => (
          <Star
            key={star}
            className={`h-4 w-4 ${star <= rating ? "fill-yellow-400 text-yellow-400" : "text-gray-300"}`}
          />
        ))}
      </div>
    )
  }

  return (
    <Card className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700">
      <CardHeader>
        <div className="flex items-center justify-between">
          <CardTitle className="text-xl font-semibold text-gray-900 dark:text-white">Session History</CardTitle>
          <div className="flex items-center gap-2">
            <div className="relative">
              <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 h-4 w-4 text-gray-400" />
              <Input
                placeholder="Search sessions..."
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                className="pl-10 w-64"
              />
            </div>
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="outline" size="sm">
                  <Filter className="h-4 w-4 mr-2" />
                  Filter
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent>
                <DropdownMenuItem onClick={() => setSelectedStatus("all")}>All Sessions</DropdownMenuItem>
                <DropdownMenuItem onClick={() => setSelectedStatus("completed")}>Completed</DropdownMenuItem>
                <DropdownMenuItem onClick={() => setSelectedStatus("cancelled")}>Cancelled</DropdownMenuItem>
                <DropdownMenuItem onClick={() => setSelectedStatus("no-show")}>No Show</DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
            <Button variant="outline" size="sm">
              <Download className="h-4 w-4 mr-2" />
              Export
            </Button>
          </div>
        </div>
      </CardHeader>
      <CardContent>
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead>
              <tr className="border-b border-gray-200 dark:border-gray-700">
                <th className="text-left py-3 px-4 font-semibold text-gray-900 dark:text-white">Student</th>
                <th className="text-left py-3 px-4 font-semibold text-gray-900 dark:text-white">Course</th>
                <th className="text-left py-3 px-4 font-semibold text-gray-900 dark:text-white">Date</th>
                <th className="text-left py-3 px-4 font-semibold text-gray-900 dark:text-white">Duration</th>
                <th className="text-left py-3 px-4 font-semibold text-gray-900 dark:text-white">Status</th>
                <th className="text-left py-3 px-4 font-semibold text-gray-900 dark:text-white">Rating</th>
                <th className="text-left py-3 px-4 font-semibold text-gray-900 dark:text-white">Notes</th>
              </tr>
            </thead>
            <tbody>
              {filteredSessions.map((session) => (
                <tr
                  key={session.id}
                  className="border-b border-gray-100 dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors"
                >
                  <td className="py-4 px-4 font-medium text-gray-900 dark:text-white">{session.student}</td>
                  <td className="py-4 px-4 text-gray-600 dark:text-gray-400">{session.course}</td>
                  <td className="py-4 px-4 text-gray-900 dark:text-white">{session.date}</td>
                  <td className="py-4 px-4 text-gray-900 dark:text-white">{session.duration}</td>
                  <td className="py-4 px-4">
                    <Badge className={getStatusColor(session.status)}>{session.status}</Badge>
                  </td>
                  <td className="py-4 px-4">{renderStars(session.rating)}</td>
                  <td className="py-4 px-4 text-gray-600 dark:text-gray-400 max-w-xs truncate">{session.notes}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </CardContent>
    </Card>
  )
}
