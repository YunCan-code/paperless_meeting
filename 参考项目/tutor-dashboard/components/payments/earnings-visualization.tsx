"use client"

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
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
  PieChart,
  Pie,
  Cell,
  ComposedChart,
} from "recharts"
import { TrendingUp, TrendingDown, Download, IndianRupee, BarChart3, Target } from "lucide-react"
import { useState } from "react"

export default function EarningsVisualization() {
  const [selectedYear, setSelectedYear] = useState("2024")
  const [selectedPeriod, setSelectedPeriod] = useState("12months")

  // Mock data for monthly earnings over multiple years
  const monthlyEarnings = [
    { month: "Jan 2023", earnings: 1850, students: 45, courses: 8, year: "2023" },
    { month: "Feb 2023", earnings: 2100, students: 52, courses: 9, year: "2023" },
    { month: "Mar 2023", earnings: 1950, students: 48, courses: 8, year: "2023" },
    { month: "Apr 2023", earnings: 2300, students: 58, courses: 10, year: "2023" },
    { month: "May 2023", earnings: 2450, students: 62, courses: 11, year: "2023" },
    { month: "Jun 2023", earnings: 2200, students: 55, courses: 10, year: "2023" },
    { month: "Jul 2023", earnings: 2600, students: 68, courses: 12, year: "2023" },
    { month: "Aug 2023", earnings: 2750, students: 72, courses: 13, year: "2023" },
    { month: "Sep 2023", earnings: 2400, students: 60, courses: 11, year: "2023" },
    { month: "Oct 2023", earnings: 2850, students: 75, courses: 14, year: "2023" },
    { month: "Nov 2023", earnings: 3100, students: 82, courses: 15, year: "2023" },
    { month: "Dec 2023", earnings: 2900, students: 78, courses: 14, year: "2023" },
    { month: "Jan 2024", earnings: 3200, students: 85, courses: 16, year: "2024" },
    { month: "Feb 2024", earnings: 3450, students: 92, courses: 17, year: "2024" },
    { month: "Mar 2024", earnings: 1365, students: 35, courses: 8, year: "2024" }, // Current month (partial)
  ]

  // Earnings by course category
  const earningsByCategory = [
    { category: "Web Development", earnings: 8500, percentage: 45, color: "#0d9488" },
    { category: "JavaScript", earnings: 5200, percentage: 28, color: "#3b82f6" },
    { category: "React", earnings: 3100, percentage: 16, color: "#8b5cf6" },
    { category: "CSS/Design", earnings: 2100, percentage: 11, color: "#f59e0b" },
  ]

  // Yearly comparison
  const yearlyComparison = [
    { year: "2022", earnings: 18500, growth: 0 },
    { year: "2023", earnings: 29500, growth: 59.5 },
    { year: "2024", earnings: 8015, growth: 35.2 }, // Projected based on current pace
  ]

  // Growth trends
  const growthTrends = [
    { period: "Q1 2023", earnings: 5900, target: 6000, growth: 12.5 },
    { period: "Q2 2023", earnings: 6950, target: 7000, growth: 17.8 },
    { period: "Q3 2023", earnings: 7750, target: 7500, growth: 11.5 },
    { period: "Q4 2023", earnings: 8850, target: 8000, growth: 14.2 },
    { period: "Q1 2024", earnings: 8015, target: 9000, growth: 35.8 },
  ]

  const filteredData = monthlyEarnings.filter((item) => (selectedYear === "all" ? true : item.year === selectedYear))

  const totalEarnings = filteredData.reduce((sum, item) => sum + item.earnings, 0)
  const averageMonthly = totalEarnings / filteredData.length
  const bestMonth = filteredData.reduce((max, item) => (item.earnings > max.earnings ? item : max), filteredData[0])
  const currentMonthGrowth =
    filteredData.length >= 2
      ? ((filteredData[filteredData.length - 1].earnings - filteredData[filteredData.length - 2].earnings) /
          filteredData[filteredData.length - 2].earnings) *
        100
      : 0

  return (
    <div className="space-y-6">
      {/* Overview Cards */}
      <div className="grid gap-4 md:grid-cols-4">
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Total Earnings</CardTitle>
            <IndianRupee className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">₹{totalEarnings.toLocaleString()}</div>
            <p className="text-xs text-muted-foreground">
              {selectedYear === "all" ? "All time" : `Year ${selectedYear}`}
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Monthly Average</CardTitle>
            <BarChart3 className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">₹{averageMonthly.toFixed(0)}</div>
            <p className="text-xs text-muted-foreground">Per month</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Best Month</CardTitle>
            <Target className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">₹{bestMonth?.earnings.toLocaleString()}</div>
            <p className="text-xs text-muted-foreground">{bestMonth?.month}</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Growth Rate</CardTitle>
            {currentMonthGrowth >= 0 ? (
              <TrendingUp className="h-4 w-4 text-green-600" />
            ) : (
              <TrendingDown className="h-4 w-4 text-red-600" />
            )}
          </CardHeader>
          <CardContent>
            <div className={`text-2xl font-bold ${currentMonthGrowth >= 0 ? "text-green-600" : "text-red-600"}`}>
              {currentMonthGrowth >= 0 ? "+" : ""}
              {currentMonthGrowth.toFixed(1)}%
            </div>
            <p className="text-xs text-muted-foreground">Month over month</p>
          </CardContent>
        </Card>
      </div>

      {/* Controls */}
      <div className="flex items-center justify-between">
        <div className="flex items-center space-x-4">
          <Select value={selectedYear} onValueChange={setSelectedYear}>
            <SelectTrigger className="w-32">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All Years</SelectItem>
              <SelectItem value="2024">2024</SelectItem>
              <SelectItem value="2023">2023</SelectItem>
              <SelectItem value="2022">2022</SelectItem>
            </SelectContent>
          </Select>

          <Select value={selectedPeriod} onValueChange={setSelectedPeriod}>
            <SelectTrigger className="w-40">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="12months">Last 12 Months</SelectItem>
              <SelectItem value="6months">Last 6 Months</SelectItem>
              <SelectItem value="3months">Last 3 Months</SelectItem>
            </SelectContent>
          </Select>
        </div>

        <Button variant="outline" className="bg-transparent">
          <Download className="mr-2 h-4 w-4" />
          Export Report
        </Button>
      </div>

      <Tabs defaultValue="monthly" className="space-y-4">
        <TabsList>
          <TabsTrigger value="monthly">Monthly Trends</TabsTrigger>
          <TabsTrigger value="categories">By Category</TabsTrigger>
          <TabsTrigger value="yearly">Yearly Comparison</TabsTrigger>
          <TabsTrigger value="growth">Growth Analysis</TabsTrigger>
        </TabsList>

        <TabsContent value="monthly" className="space-y-4">
          <Card>
            <CardHeader>
              <CardTitle>Monthly Earnings Trend</CardTitle>
              <CardDescription>Track your earnings progression over time</CardDescription>
            </CardHeader>
            <CardContent>
              <div className="h-80">
                <ResponsiveContainer width="100%" height="100%">
                  <ComposedChart data={filteredData}>
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="month" tick={{ fontSize: 12 }} angle={-45} textAnchor="end" height={60} />
                    <YAxis />
                    <Tooltip
                      formatter={(value, name) => [
                        name === "earnings" ? `₹${value}` : value,
                        name === "earnings" ? "Earnings" : name === "students" ? "Students" : "Courses",
                      ]}
                      labelStyle={{ fontWeight: "bold" }}
                    />
                    <Area
                      type="monotone"
                      dataKey="earnings"
                      fill="#0d9488"
                      fillOpacity={0.3}
                      stroke="#0d9488"
                      strokeWidth={2}
                    />
                    <Bar dataKey="students" fill="#3b82f6" opacity={0.6} />
                  </ComposedChart>
                </ResponsiveContainer>
              </div>
            </CardContent>
          </Card>

          <div className="grid gap-4 md:grid-cols-2">
            <Card>
              <CardHeader>
                <CardTitle>Student Engagement</CardTitle>
                <CardDescription>Monthly student count trends</CardDescription>
              </CardHeader>
              <CardContent>
                <div className="h-64">
                  <ResponsiveContainer width="100%" height="100%">
                    <LineChart data={filteredData}>
                      <CartesianGrid strokeDasharray="3 3" />
                      <XAxis dataKey="month" tick={{ fontSize: 10 }} />
                      <YAxis />
                      <Tooltip formatter={(value) => [`₹${value}`, "Monthly Earnings"]} />
                      <Line
                        type="monotone"
                        dataKey="students"
                        stroke="#3b82f6"
                        strokeWidth={3}
                        dot={{ fill: "#3b82f6", strokeWidth: 2, r: 4 }}
                      />
                    </LineChart>
                  </ResponsiveContainer>
                </div>
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>Course Activity</CardTitle>
                <CardDescription>Number of active courses per month</CardDescription>
              </CardHeader>
              <CardContent>
                <div className="h-64">
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart data={filteredData}>
                      <CartesianGrid strokeDasharray="3 3" />
                      <XAxis dataKey="month" tick={{ fontSize: 10 }} />
                      <YAxis />
                      <Tooltip formatter={(value) => [`₹${value}`, "Earnings"]} />
                      <Bar dataKey="courses" fill="#8b5cf6" />
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              </CardContent>
            </Card>
          </div>
        </TabsContent>

        <TabsContent value="categories" className="space-y-4">
          <div className="grid gap-4 md:grid-cols-2">
            <Card>
              <CardHeader>
                <CardTitle>Earnings by Category</CardTitle>
                <CardDescription>Revenue distribution across course categories</CardDescription>
              </CardHeader>
              <CardContent>
                <div className="h-80">
                  <ResponsiveContainer width="100%" height="100%">
                    <PieChart>
                      <Pie
                        data={earningsByCategory}
                        cx="50%"
                        cy="50%"
                        labelLine={false}
                        label={({ category, percentage }) => `${category} (${percentage}%)`}
                        outerRadius={80}
                        fill="#8884d8"
                        dataKey="earnings"
                      >
                        {earningsByCategory.map((entry, index) => (
                          <Cell key={`cell-${index}`} fill={entry.color} />
                        ))}
                      </Pie>
                      <Tooltip formatter={(value) => [`₹${value}`, "Earnings"]} />
                    </PieChart>
                  </ResponsiveContainer>
                </div>
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>Category Performance</CardTitle>
                <CardDescription>Detailed breakdown by course category</CardDescription>
              </CardHeader>
              <CardContent>
                <div className="space-y-4">
                  {earningsByCategory.map((category, index) => (
                    <div key={index} className="space-y-2">
                      <div className="flex items-center justify-between">
                        <div className="flex items-center space-x-3">
                          <div className="w-4 h-4 rounded-full" style={{ backgroundColor: category.color }} />
                          <span className="font-medium">{category.category}</span>
                        </div>
                        <div className="text-right">
                          <p className="font-semibold">₹{category.earnings.toLocaleString()}</p>
                          <Badge variant="secondary">{category.percentage}%</Badge>
                        </div>
                      </div>
                      <div className="w-full bg-gray-200 rounded-full h-2 dark:bg-gray-700">
                        <div
                          className="h-2 rounded-full"
                          style={{
                            width: `${category.percentage}%`,
                            backgroundColor: category.color,
                          }}
                        />
                      </div>
                    </div>
                  ))}
                </div>
              </CardContent>
            </Card>
          </div>
        </TabsContent>

        <TabsContent value="yearly" className="space-y-4">
          <Card>
            <CardHeader>
              <CardTitle>Year-over-Year Comparison</CardTitle>
              <CardDescription>Annual earnings growth and trends</CardDescription>
            </CardHeader>
            <CardContent>
              <div className="h-80">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={yearlyComparison}>
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="year" />
                    <YAxis />
                    <Tooltip
                      formatter={(value, name) => [
                        name === "earnings" ? `₹${value.toLocaleString()}` : `${value}%`,
                        name === "earnings" ? "Total Earnings" : "Growth Rate",
                      ]}
                    />
                    <Bar dataKey="earnings" fill="#0d9488" />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            </CardContent>
          </Card>

          <div className="grid gap-4 md:grid-cols-3">
            {yearlyComparison.map((year, index) => (
              <Card key={index}>
                <CardHeader className="pb-2">
                  <CardTitle className="text-lg">{year.year}</CardTitle>
                </CardHeader>
                <CardContent>
                  <div className="space-y-2">
                    <div className="text-2xl font-bold">₹{year.earnings.toLocaleString()}</div>
                    {year.growth > 0 && (
                      <div className="flex items-center space-x-1">
                        <TrendingUp className="h-4 w-4 text-green-600" />
                        <span className="text-sm text-green-600 font-medium">+{year.growth}%</span>
                      </div>
                    )}
                    <p className="text-xs text-muted-foreground">
                      {year.year === "2024" ? "Projected" : "Total earnings"}
                    </p>
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>
        </TabsContent>

        <TabsContent value="growth" className="space-y-4">
          <Card>
            <CardHeader>
              <CardTitle>Quarterly Growth Analysis</CardTitle>
              <CardDescription>Performance vs targets and growth trends</CardDescription>
            </CardHeader>
            <CardContent>
              <div className="h-80">
                <ResponsiveContainer width="100%" height="100%">
                  <ComposedChart data={growthTrends}>
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="period" />
                    <YAxis />
                    <Tooltip
                      formatter={(value, name) => [
                        name === "growth" ? `${value}%` : `₹${value}`,
                        name === "earnings" ? "Actual" : name === "target" ? "Target" : "Growth Rate",
                      ]}
                    />
                    <Bar dataKey="earnings" fill="#0d9488" />
                    <Bar dataKey="target" fill="#94a3b8" opacity={0.5} />
                    <Line type="monotone" dataKey="growth" stroke="#f59e0b" strokeWidth={3} />
                  </ComposedChart>
                </ResponsiveContainer>
              </div>
            </CardContent>
          </Card>

          <div className="grid gap-4 md:grid-cols-2">
            <Card>
              <CardHeader>
                <CardTitle>Growth Metrics</CardTitle>
                <CardDescription>Key performance indicators</CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="flex justify-between items-center">
                  <span>Average Quarterly Growth</span>
                  <span className="font-semibold text-green-600">+20.2%</span>
                </div>
                <div className="flex justify-between items-center">
                  <span>Best Quarter</span>
                  <span className="font-semibold">Q1 2024 (+35.8%)</span>
                </div>
                <div className="flex justify-between items-center">
                  <span>Target Achievement</span>
                  <span className="font-semibold">89%</span>
                </div>
                <div className="flex justify-between items-center">
                  <span>Projected Annual</span>
                  <span className="font-semibold">₹32,000</span>
                </div>
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>Next Quarter Forecast</CardTitle>
                <CardDescription>Q2 2024 projections</CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="text-center">
                  <div className="text-3xl font-bold text-blue-600">₹9,500</div>
                  <p className="text-sm text-muted-foreground">Projected earnings</p>
                </div>
                <div className="space-y-2">
                  <div className="flex justify-between text-sm">
                    <span>Confidence Level</span>
                    <span>85%</span>
                  </div>
                  <div className="w-full bg-gray-200 rounded-full h-2 dark:bg-gray-700">
                    <div className="bg-blue-600 h-2 rounded-full" style={{ width: "85%" }} />
                  </div>
                </div>
                <p className="text-xs text-muted-foreground">Based on current trends and historical data</p>
              </CardContent>
            </Card>
          </div>
        </TabsContent>
      </Tabs>
    </div>
  )
}
