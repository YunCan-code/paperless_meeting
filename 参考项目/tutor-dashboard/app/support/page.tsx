"use client"

import type React from "react"
import { DashboardLayout } from "@/components/dashboard-layout"

import { useState } from "react"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Badge } from "@/components/ui/badge"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import {
  AlertCircle,
  CheckCircle,
  Clock,
  Send,
  MessageSquare,
  Bug,
  CreditCard,
  Users,
  BookOpen,
  Headphones,
  Star,
  TrendingUp,
} from "lucide-react"

interface Complaint {
  id: string
  title: string
  category: string
  priority: "low" | "medium" | "high"
  status: "pending" | "in-progress" | "resolved"
  description: string
  createdAt: string
  updatedAt: string
  response?: string
}

const mockComplaints: Complaint[] = [
  {
    id: "1",
    title: "Payment not received for last week",
    category: "payment",
    priority: "high",
    status: "in-progress",
    description: "I haven't received my payment for the week ending March 15th. The amount should be ₹15,000.",
    createdAt: "2024-03-18",
    updatedAt: "2024-03-19",
    response: "We're investigating this issue. Payment should be processed within 24 hours.",
  },
  {
    id: "2",
    title: "Student progress not updating",
    category: "technical",
    priority: "medium",
    status: "resolved",
    description: "Student progress bars are not updating properly in the dashboard.",
    createdAt: "2024-03-15",
    updatedAt: "2024-03-16",
    response: "This issue has been fixed. Please refresh your browser and check again.",
  },
  {
    id: "3",
    title: "Unable to schedule sessions",
    category: "technical",
    priority: "high",
    status: "pending",
    description: "The session scheduling feature is not working. Getting error when trying to create new sessions.",
    createdAt: "2024-03-20",
    updatedAt: "2024-03-20",
  },
]

export default function SupportPage() {
  const [complaints, setComplaints] = useState<Complaint[]>(mockComplaints)
  const [newComplaint, setNewComplaint] = useState({
    title: "",
    category: "",
    priority: "medium" as const,
    description: "",
  })

  const handleSubmitComplaint = (e: React.FormEvent) => {
    e.preventDefault()

    const complaint: Complaint = {
      id: Date.now().toString(),
      title: newComplaint.title,
      category: newComplaint.category,
      priority: newComplaint.priority,
      status: "pending",
      description: newComplaint.description,
      createdAt: new Date().toISOString().split("T")[0],
      updatedAt: new Date().toISOString().split("T")[0],
    }

    setComplaints([complaint, ...complaints])
    setNewComplaint({ title: "", category: "", priority: "medium", description: "" })
  }

  const getStatusIcon = (status: string) => {
    switch (status) {
      case "pending":
        return <Clock className="h-4 w-4" />
      case "in-progress":
        return <AlertCircle className="h-4 w-4" />
      case "resolved":
        return <CheckCircle className="h-4 w-4" />
      default:
        return <Clock className="h-4 w-4" />
    }
  }

  const getStatusColor = (status: string) => {
    switch (status) {
      case "pending":
        return "bg-yellow-100 text-yellow-800 dark:bg-yellow-900 dark:text-yellow-300"
      case "in-progress":
        return "bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-300"
      case "resolved":
        return "bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-300"
      default:
        return "bg-gray-100 text-gray-800 dark:bg-gray-900 dark:text-gray-300"
    }
  }

  const getPriorityColor = (priority: string) => {
    switch (priority) {
      case "high":
        return "bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-300"
      case "medium":
        return "bg-orange-100 text-orange-800 dark:bg-orange-900 dark:text-orange-300"
      case "low":
        return "bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-300"
      default:
        return "bg-gray-100 text-gray-800 dark:bg-gray-900 dark:text-gray-300"
    }
  }

  const getCategoryIcon = (category: string) => {
    switch (category) {
      case "payment":
        return <CreditCard className="h-4 w-4" />
      case "technical":
        return <Bug className="h-4 w-4" />
      case "student":
        return <Users className="h-4 w-4" />
      case "course":
        return <BookOpen className="h-4 w-4" />
      default:
        return <MessageSquare className="h-4 w-4" />
    }
  }

  return (
    <DashboardLayout>
      <div className="space-y-8">
        <div className="relative overflow-hidden rounded-2xl bg-gradient-to-br from-teal-500 via-teal-600 to-emerald-600 p-8 text-white">
          <div className="absolute inset-0 bg-black/10"></div>
          <div className="relative z-10">
            <div className="flex items-center gap-3 mb-4">
              <div className="p-3 bg-white/20 rounded-xl backdrop-blur-sm">
                <Headphones className="h-8 w-8" />
              </div>
              <div>
                <h1 className="text-4xl font-bold tracking-tight">Support Center</h1>
                <p className="text-teal-100 text-lg">We're here to help you succeed</p>
              </div>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mt-6">
              <div className="bg-white/10 backdrop-blur-sm rounded-xl p-4">
                <div className="flex items-center gap-2">
                  <Clock className="h-5 w-5 text-teal-200" />
                  <span className="text-sm font-medium text-teal-100">Avg Response Time</span>
                </div>
                <p className="text-2xl font-bold mt-1">2 hours</p>
              </div>
              <div className="bg-white/10 backdrop-blur-sm rounded-xl p-4">
                <div className="flex items-center gap-2">
                  <Star className="h-5 w-5 text-teal-200" />
                  <span className="text-sm font-medium text-teal-100">Satisfaction Rate</span>
                </div>
                <p className="text-2xl font-bold mt-1">98%</p>
              </div>
              <div className="bg-white/10 backdrop-blur-sm rounded-xl p-4">
                <div className="flex items-center gap-2">
                  <TrendingUp className="h-5 w-5 text-teal-200" />
                  <span className="text-sm font-medium text-teal-100">Issues Resolved</span>
                </div>
                <p className="text-2xl font-bold mt-1">1,247</p>
              </div>
            </div>
          </div>
        </div>

        <Tabs defaultValue="submit" className="space-y-8">
          <TabsList className="grid w-full grid-cols-2 h-12 p-1 bg-muted/50">
            <TabsTrigger value="submit" className="flex items-center gap-2 text-base font-medium">
              <Send className="h-4 w-4" />
              Submit Complaint
            </TabsTrigger>
            <TabsTrigger value="history" className="flex items-center gap-2 text-base font-medium">
              <MessageSquare className="h-4 w-4" />
              My Complaints
            </TabsTrigger>
          </TabsList>

          <TabsContent value="submit">
            <Card className="border-0 shadow-lg bg-gradient-to-br from-white to-gray-50/50 dark:from-gray-900 dark:to-gray-800/50">
              <CardHeader className="pb-6">
                <div className="flex items-center gap-3">
                  <div className="p-2 bg-teal-100 dark:bg-teal-900/30 rounded-lg">
                    <Send className="h-6 w-6 text-teal-600 dark:text-teal-400" />
                  </div>
                  <div>
                    <CardTitle className="text-2xl">Submit New Complaint</CardTitle>
                    <CardDescription className="text-base mt-1">
                      Describe your issue and we'll get back to you as soon as possible
                    </CardDescription>
                  </div>
                </div>
              </CardHeader>
              <CardContent className="space-y-6">
                <form onSubmit={handleSubmitComplaint} className="space-y-6">
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                    <div className="space-y-3">
                      <Label htmlFor="title" className="text-base font-medium">
                        Issue Title
                      </Label>
                      <Input
                        id="title"
                        placeholder="Brief description of your issue"
                        value={newComplaint.title}
                        onChange={(e) => setNewComplaint({ ...newComplaint, title: e.target.value })}
                        className="h-12 text-base"
                        required
                      />
                    </div>
                    <div className="space-y-3">
                      <Label htmlFor="category" className="text-base font-medium">
                        Category
                      </Label>
                      <Select
                        value={newComplaint.category}
                        onValueChange={(value) => setNewComplaint({ ...newComplaint, category: value })}
                        required
                      >
                        <SelectTrigger className="h-12 text-base">
                          <SelectValue placeholder="Select category" />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectItem value="payment" className="flex items-center gap-2">
                            <CreditCard className="h-4 w-4" />
                            Payment Issues
                          </SelectItem>
                          <SelectItem value="technical">
                            <Bug className="h-4 w-4" />
                            Technical Problems
                          </SelectItem>
                          <SelectItem value="student">
                            <Users className="h-4 w-4" />
                            Student Management
                          </SelectItem>
                          <SelectItem value="course">
                            <BookOpen className="h-4 w-4" />
                            Course Content
                          </SelectItem>
                          <SelectItem value="other">Other</SelectItem>
                        </SelectContent>
                      </Select>
                    </div>
                  </div>

                  <div className="space-y-3">
                    <Label htmlFor="priority" className="text-base font-medium">
                      Priority Level
                    </Label>
                    <Select
                      value={newComplaint.priority}
                      onValueChange={(value: "low" | "medium" | "high") =>
                        setNewComplaint({ ...newComplaint, priority: value })
                      }
                    >
                      <SelectTrigger className="w-full md:w-64 h-12 text-base">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="low">🟢 Low Priority</SelectItem>
                        <SelectItem value="medium">🟡 Medium Priority</SelectItem>
                        <SelectItem value="high">🔴 High Priority</SelectItem>
                      </SelectContent>
                    </Select>
                  </div>

                  <div className="space-y-3">
                    <Label htmlFor="description" className="text-base font-medium">
                      Detailed Description
                    </Label>
                    <Textarea
                      id="description"
                      placeholder="Please provide detailed information about your issue..."
                      rows={6}
                      value={newComplaint.description}
                      onChange={(e) => setNewComplaint({ ...newComplaint, description: e.target.value })}
                      className="text-base resize-none"
                      required
                    />
                  </div>

                  <Button
                    type="submit"
                    className="w-full md:w-auto h-12 px-8 text-base font-medium bg-gradient-to-r from-teal-600 to-emerald-600 hover:from-teal-700 hover:to-emerald-700 shadow-lg"
                  >
                    <Send className="h-5 w-5 mr-2" />
                    Submit Complaint
                  </Button>
                </form>
              </CardContent>
            </Card>
          </TabsContent>

          <TabsContent value="history">
            <div className="space-y-6">
              <div className="flex items-center justify-between p-6 bg-gradient-to-r from-gray-50 to-gray-100 dark:from-gray-800 dark:to-gray-700 rounded-xl border">
                <div className="flex items-center gap-3">
                  <div className="p-2 bg-teal-100 dark:bg-teal-900/30 rounded-lg">
                    <MessageSquare className="h-6 w-6 text-teal-600 dark:text-teal-400" />
                  </div>
                  <div>
                    <h2 className="text-2xl font-bold">Your Complaints History</h2>
                    <p className="text-muted-foreground">Track the status of your submitted issues</p>
                  </div>
                </div>
                <Badge variant="outline" className="px-4 py-2 text-base font-medium">
                  {complaints.length} Total Complaints
                </Badge>
              </div>

              <div className="grid gap-6">
                {complaints.map((complaint) => (
                  <Card
                    key={complaint.id}
                    className="border-0 shadow-lg hover:shadow-xl transition-all duration-300 bg-gradient-to-br from-white to-gray-50/30 dark:from-gray-900 dark:to-gray-800/30"
                  >
                    <CardHeader className="pb-4">
                      <div className="flex items-start justify-between">
                        <div className="space-y-3">
                          <CardTitle className="text-xl flex items-center gap-3">
                            <div className="p-2 bg-teal-100 dark:bg-teal-900/30 rounded-lg">
                              {getCategoryIcon(complaint.category)}
                            </div>
                            {complaint.title}
                          </CardTitle>
                          <div className="flex items-center gap-3 flex-wrap">
                            <Badge className={`${getStatusColor(complaint.status)} px-3 py-1 text-sm font-medium`}>
                              {getStatusIcon(complaint.status)}
                              <span className="ml-2 capitalize">{complaint.status.replace("-", " ")}</span>
                            </Badge>
                            <Badge className={`${getPriorityColor(complaint.priority)} px-3 py-1 text-sm font-medium`}>
                              {complaint.priority.toUpperCase()} Priority
                            </Badge>
                            <Badge variant="outline" className="px-3 py-1 text-sm">
                              ID: #{complaint.id}
                            </Badge>
                          </div>
                        </div>
                        <div className="text-right text-sm text-muted-foreground bg-muted/30 p-3 rounded-lg">
                          <div className="font-medium">Created: {complaint.createdAt}</div>
                          <div>Updated: {complaint.updatedAt}</div>
                        </div>
                      </div>
                    </CardHeader>
                    <CardContent className="space-y-4">
                      <div className="bg-muted/20 p-4 rounded-lg">
                        <h4 className="font-semibold mb-2 text-base">Description:</h4>
                        <p className="text-muted-foreground leading-relaxed">{complaint.description}</p>
                      </div>
                      {complaint.response && (
                        <div className="bg-gradient-to-r from-green-50 to-emerald-50 dark:from-green-900/20 dark:to-emerald-900/20 p-4 rounded-lg border border-green-200 dark:border-green-800">
                          <h4 className="font-semibold mb-2 text-green-700 dark:text-green-400 flex items-center gap-2">
                            <CheckCircle className="h-4 w-4" />
                            Admin Response:
                          </h4>
                          <p className="text-green-800 dark:text-green-300 leading-relaxed">{complaint.response}</p>
                        </div>
                      )}
                    </CardContent>
                  </Card>
                ))}
              </div>
            </div>
          </TabsContent>
        </Tabs>
      </div>
    </DashboardLayout>
  )
}
