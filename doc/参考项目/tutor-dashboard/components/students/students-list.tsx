"use client"

import { useState } from "react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Progress } from "@/components/ui/progress"
import { Search, Filter, MoreHorizontal } from "lucide-react"
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from "@/components/ui/dropdown-menu"

const studentsData = [
  {
    id: 1,
    name: "Alice Johnson",
    email: "alice@example.com",
    avatar: "https://i.pravatar.cc/60?img=1",
    course: "React for Beginners",
    progress: 85,
    lastActive: "2 hours ago",
    status: "active",
    studyTime: "12.5h",
  },
  {
    id: 2,
    name: "Bob Smith",
    email: "bob@example.com",
    avatar: "https://i.pravatar.cc/60?img=2",
    course: "JavaScript Fundamentals",
    progress: 62,
    lastActive: "1 day ago",
    status: "active",
    studyTime: "8.2h",
  },
  {
    id: 3,
    name: "Carol Davis",
    email: "carol@example.com",
    avatar: "https://i.pravatar.cc/60?img=3",
    course: "HTML & CSS Essentials",
    progress: 100,
    lastActive: "3 days ago",
    status: "completed",
    studyTime: "15.7h",
  },
  {
    id: 4,
    name: "David Wilson",
    email: "david@example.com",
    avatar: "https://i.pravatar.cc/60?img=4",
    course: "Modern JavaScript",
    progress: 23,
    lastActive: "1 week ago",
    status: "inactive",
    studyTime: "3.1h",
  },
  {
    id: 5,
    name: "Eva Brown",
    email: "eva@example.com",
    avatar: "https://i.pravatar.cc/60?img=5",
    course: "React for Beginners",
    progress: 45,
    lastActive: "5 hours ago",
    status: "active",
    studyTime: "6.8h",
  },
]

export function StudentsList() {
  const [searchTerm, setSearchTerm] = useState("")
  const [selectedStatus, setSelectedStatus] = useState("all")

  const filteredStudents = studentsData.filter((student) => {
    const matchesSearch =
      student.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
      student.email.toLowerCase().includes(searchTerm.toLowerCase())
    const matchesStatus = selectedStatus === "all" || student.status === selectedStatus
    return matchesSearch && matchesStatus
  })

  const getStatusColor = (status: string) => {
    switch (status) {
      case "active":
        return "bg-green-100 text-green-800 dark:bg-green-900/20 dark:text-green-400"
      case "completed":
        return "bg-blue-100 text-blue-800 dark:bg-blue-900/20 dark:text-blue-400"
      case "inactive":
        return "bg-gray-100 text-gray-800 dark:bg-gray-900/20 dark:text-gray-400"
      default:
        return "bg-gray-100 text-gray-800 dark:bg-gray-900/20 dark:text-gray-400"
    }
  }

  return (
    <Card className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700">
      <CardHeader>
        <div className="flex items-center justify-between">
          <CardTitle className="text-xl font-semibold text-gray-900 dark:text-white">Students</CardTitle>
          <div className="flex items-center gap-2">
            <div className="relative">
              <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 h-4 w-4 text-gray-400" />
              <Input
                placeholder="Search students..."
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
                <DropdownMenuItem onClick={() => setSelectedStatus("all")}>All Students</DropdownMenuItem>
                <DropdownMenuItem onClick={() => setSelectedStatus("active")}>Active</DropdownMenuItem>
                <DropdownMenuItem onClick={() => setSelectedStatus("completed")}>Completed</DropdownMenuItem>
                <DropdownMenuItem onClick={() => setSelectedStatus("inactive")}>Inactive</DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </div>
        </div>
      </CardHeader>
      <CardContent>
        <div className="space-y-4">
          {filteredStudents.map((student) => (
            <div
              key={student.id}
              className="flex items-center justify-between p-4 border border-gray-200 dark:border-gray-700 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors"
            >
              <div className="flex items-center gap-4">
                <img
                  src={student.avatar || "/placeholder.svg"}
                  alt={student.name}
                  className="w-12 h-12 rounded-full object-cover"
                />
                <div className="space-y-1">
                  <div className="flex items-center gap-2">
                    <h3 className="font-semibold text-gray-900 dark:text-white">{student.name}</h3>
                    <Badge className={getStatusColor(student.status)}>{student.status}</Badge>
                  </div>
                  <p className="text-sm text-gray-500 dark:text-gray-400">{student.email}</p>
                  <p className="text-sm text-gray-600 dark:text-gray-300">{student.course}</p>
                </div>
              </div>

              <div className="flex items-center gap-6">
                <div className="text-right">
                  <p className="text-sm font-medium text-gray-900 dark:text-white">Progress</p>
                  <div className="flex items-center gap-2 mt-1">
                    <Progress value={student.progress} className="w-20" />
                    <span className="text-sm text-gray-600 dark:text-gray-400">{student.progress}%</span>
                  </div>
                </div>

                <div className="text-right">
                  <p className="text-sm font-medium text-gray-900 dark:text-white">Study Time</p>
                  <p className="text-sm text-gray-600 dark:text-gray-400">{student.studyTime}</p>
                </div>

                <div className="text-right">
                  <p className="text-sm font-medium text-gray-900 dark:text-white">Last Active</p>
                  <p className="text-sm text-gray-600 dark:text-gray-400">{student.lastActive}</p>
                </div>

                <DropdownMenu>
                  <DropdownMenuTrigger asChild>
                    <Button variant="ghost" size="sm">
                      <MoreHorizontal className="h-4 w-4" />
                    </Button>
                  </DropdownMenuTrigger>
                  <DropdownMenuContent>
                    <DropdownMenuItem>View Details</DropdownMenuItem>
                    <DropdownMenuItem>Send Message</DropdownMenuItem>
                    <DropdownMenuItem>View Progress</DropdownMenuItem>
                  </DropdownMenuContent>
                </DropdownMenu>
              </div>
            </div>
          ))}
        </div>
      </CardContent>
    </Card>
  )
}
