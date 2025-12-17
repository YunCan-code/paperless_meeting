"use client"

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Progress } from "@/components/ui/progress"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  LineChart,
  Line,
  Area,
  AreaChart,
} from "recharts"
import { Calendar, TrendingUp, Clock, CheckCircle, AlertCircle, Download, Eye } from "lucide-react"
import { useState } from "react"

export default function WeeklyPaymentTracker() {
  const [selectedWeek, setSelectedWeek] = useState<number | null>(null)

  // Mock data for weekly payments
  const weeklyData = [
    {
      weekNumber: 1,
      dateRange: "Mar 1-7, 2024",
      earnings: 645.3,
      courses: 8,
      students: 24,
      hours: 18.5,
      status: "paid",
      payoutDate: "Mar 8, 2024",
      dailyBreakdown: [
        { day: "Mon", amount: 125.5, hours: 3.2 },
        { day: "Tue", amount: 89.2, hours: 2.8 },
        { day: "Wed", amount: 156.8, hours: 4.1 },
        { day: "Thu", amount: 98.4, hours: 2.9 },
        { day: "Fri", amount: 134.7, hours: 3.8 },
        { day: "Sat", amount: 40.7, hours: 1.7 },
        { day: "Sun", amount: 0, hours: 0 },
      ],
    },
    {
      weekNumber: 2,
      dateRange: "Mar 8-14, 2024",
      earnings: 485.2,
      courses: 6,
      students: 19,
      hours: 14.2,
      status: "pending",
      payoutDate: "Mar 15, 2024",
      dailyBreakdown: [
        { day: "Mon", amount: 98.5, hours: 2.8 },
        { day: "Tue", amount: 112.3, hours: 3.4 },
        { day: "Wed", amount: 87.9, hours: 2.6 },
        { day: "Thu", amount: 124.6, hours: 3.7 },
        { day: "Fri", amount: 61.9, hours: 1.7 },
        { day: "Sat", amount: 0, hours: 0 },
        { day: "Sun", amount: 0, hours: 0 },
      ],
    },
    {
      weekNumber: 3,
      dateRange: "Mar 15-21, 2024",
      earnings: 234.8,
      courses: 4,
      students: 12,
      hours: 8.3,
      status: "current",
      payoutDate: "Mar 22, 2024",
      dailyBreakdown: [
        { day: "Mon", amount: 89.4, hours: 2.5 },
        { day: "Tue", amount: 76.2, hours: 2.1 },
        { day: "Wed", amount: 69.2, hours: 1.9 },
        { day: "Thu", amount: 0, hours: 0 },
        { day: "Fri", amount: 0, hours: 0 },
        { day: "Sat", amount: 0, hours: 0 },
        { day: "Sun", amount: 0, hours: 0 },
      ],
    },
    {
      weekNumber: 4,
      dateRange: "Mar 22-28, 2024",
      earnings: 0,
      courses: 0,
      students: 0,
      hours: 0,
      status: "upcoming",
      payoutDate: "Mar 29, 2024",
      dailyBreakdown: [
        { day: "Mon", amount: 0, hours: 0 },
        { day: "Tue", amount: 0, hours: 0 },
        { day: "Wed", amount: 0, hours: 0 },
        { day: "Thu", amount: 0, hours: 0 },
        { day: "Fri", amount: 0, hours: 0 },
        { day: "Sat", amount: 0, hours: 0 },
        { day: "Sun", amount: 0, hours: 0 },
      ],
    },
  ]

  const monthlyTrend = [
    { month: "Jan", earnings: 2340, weeks: 4 },
    { month: "Feb", earnings: 2890, weeks: 4 },
    { month: "Mar", earnings: 1365, weeks: 3 }, // Current month (partial)
  ]

  const getStatusIcon = (status: string) => {
    switch (status) {
      case "paid":
        return <CheckCircle className="h-4 w-4 text-green-600" />
      case "pending":
        return <Clock className="h-4 w-4 text-yellow-600" />
      case "current":
        return <TrendingUp className="h-4 w-4 text-blue-600" />
      default:
        return <AlertCircle className="h-4 w-4 text-gray-400" />
    }
  }

  const getStatusColor = (status: string) => {
    switch (status) {
      case "paid":
        return "bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200"
      case "pending":
        return "bg-yellow-100 text-yellow-800 dark:bg-yellow-900 dark:text-yellow-200"
      case "current":
        return "bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-200"
      default:
        return "bg-gray-100 text-gray-800 dark:bg-gray-800 dark:text-gray-200"
    }
  }

  return (
    <div className="space-y-6">
      {/* Weekly Overview Cards */}
      <div className="grid gap-4 md:grid-cols-4">
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">This Week</CardTitle>
            <Calendar className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">₹{weeklyData[2].earnings.toFixed(2)}</div>
            <p className="text-xs text-muted-foreground">3 days remaining</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Weekly Average</CardTitle>
            <TrendingUp className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">
              ₹{(weeklyData.slice(0, 2).reduce((sum, week) => sum + week.earnings, 0) / 2).toFixed(2)}
            </div>
            <p className="text-xs text-muted-foreground">Last 2 weeks</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Total Students</CardTitle>
            <Eye className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{weeklyData[2].students}</div>
            <p className="text-xs text-muted-foreground">This week</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Teaching Hours</CardTitle>
            <Clock className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{weeklyData[2].hours}h</div>
            <p className="text-xs text-muted-foreground">This week</p>
          </CardContent>
        </Card>
      </div>

      <Tabs defaultValue="weekly" className="space-y-4">
        <TabsList>
          <TabsTrigger value="weekly">Weekly Breakdown</TabsTrigger>
          <TabsTrigger value="daily">Daily Analysis</TabsTrigger>
          <TabsTrigger value="trends">Monthly Trends</TabsTrigger>
        </TabsList>

        <TabsContent value="weekly" className="space-y-4">
          <div className="grid gap-4">
            {weeklyData.map((week) => (
              <Card key={week.weekNumber} className="cursor-pointer hover:shadow-md transition-shadow">
                <CardHeader>
                  <div className="flex items-center justify-between">
                    <div className="flex items-center space-x-3">
                      {getStatusIcon(week.status)}
                      <div>
                        <CardTitle className="text-lg">Week {week.weekNumber}</CardTitle>
                        <CardDescription>{week.dateRange}</CardDescription>
                      </div>
                    </div>
                    <div className="text-right">
                      <div className="text-2xl font-bold">₹{week.earnings.toFixed(2)}</div>
                      <Badge className={getStatusColor(week.status)}>{week.status}</Badge>
                    </div>
                  </div>
                </CardHeader>
                <CardContent>
                  <div className="grid gap-4 md:grid-cols-4">
                    <div className="space-y-1">
                      <p className="text-sm text-muted-foreground">Courses</p>
                      <p className="text-lg font-semibold">{week.courses}</p>
                    </div>
                    <div className="space-y-1">
                      <p className="text-sm text-muted-foreground">Students</p>
                      <p className="text-lg font-semibold">{week.students}</p>
                    </div>
                    <div className="space-y-1">
                      <p className="text-sm text-muted-foreground">Hours</p>
                      <p className="text-lg font-semibold">{week.hours}h</p>
                    </div>
                    <div className="space-y-1">
                      <p className="text-sm text-muted-foreground">Payout Date</p>
                      <p className="text-lg font-semibold">{week.payoutDate}</p>
                    </div>
                  </div>

                  {week.status === "current" && (
                    <div className="mt-4 space-y-2">
                      <div className="flex justify-between text-sm">
                        <span>Week Progress</span>
                        <span>3/7 days</span>
                      </div>
                      <Progress value={43} className="h-2" />
                    </div>
                  )}

                  <div className="mt-4 flex justify-between items-center">
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => setSelectedWeek(selectedWeek === week.weekNumber ? null : week.weekNumber)}
                      className="bg-transparent"
                    >
                      {selectedWeek === week.weekNumber ? "Hide Details" : "View Details"}
                    </Button>
                    {week.status === "paid" && (
                      <Button variant="outline" size="sm" className="bg-transparent">
                        <Download className="mr-2 h-4 w-4" />
                        Receipt
                      </Button>
                    )}
                  </div>

                  {selectedWeek === week.weekNumber && (
                    <div className="mt-4 pt-4 border-t">
                      <h4 className="font-medium mb-3">Daily Breakdown</h4>
                      <div className="h-64">
                        <ResponsiveContainer width="100%" height="100%">
                          <BarChart data={week.dailyBreakdown}>
                            <CartesianGrid strokeDasharray="3 3" />
                            <XAxis dataKey="day" />
                            <YAxis />
                            <Tooltip
                              formatter={(value, name) => [
                                name === "amount" ? `₹${value}` : `${value}h`,
                                name === "amount" ? "Earnings" : "Hours",
                              ]}
                            />
                            <Bar dataKey="amount" fill="#0d9488" />
                          </BarChart>
                        </ResponsiveContainer>
                      </div>
                    </div>
                  )}
                </CardContent>
              </Card>
            ))}
          </div>
        </TabsContent>

        <TabsContent value="daily" className="space-y-4">
          <Card>
            <CardHeader>
              <CardTitle>Daily Earnings Pattern</CardTitle>
              <CardDescription>Your earnings distribution across weekdays</CardDescription>
            </CardHeader>
            <CardContent>
              <div className="h-80">
                <ResponsiveContainer width="100%" height="100%">
                  <AreaChart data={weeklyData[2].dailyBreakdown}>
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="day" />
                    <YAxis />
                    <Tooltip formatter={(value) => [`₹${value}`, "Earnings"]} />
                    <Area type="monotone" dataKey="amount" stroke="#0d9488" fill="#0d9488" fillOpacity={0.3} />
                  </AreaChart>
                </ResponsiveContainer>
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="trends" className="space-y-4">
          <Card>
            <CardHeader>
              <CardTitle>Monthly Earnings Trend</CardTitle>
              <CardDescription>Track your monthly performance over time</CardDescription>
            </CardHeader>
            <CardContent>
              <div className="h-80">
                <ResponsiveContainer width="100%" height="100%">
                  <LineChart data={monthlyTrend}>
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="month" />
                    <YAxis />
                    <Tooltip formatter={(value) => [`₹${value}`, "Monthly Earnings"]} />
                    <Line type="monotone" dataKey="earnings" stroke="#0d9488" strokeWidth={3} />
                  </LineChart>
                </ResponsiveContainer>
              </div>
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  )
}
