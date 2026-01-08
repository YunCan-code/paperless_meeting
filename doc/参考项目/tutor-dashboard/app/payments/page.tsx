"use client"

import { DashboardLayout } from "@/components/dashboard-layout"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Progress } from "@/components/ui/progress"
import { IndianRupee, TrendingUp, Calendar, Download, CreditCard, Clock, CheckCircle, AlertCircle } from "lucide-react"

export default function PaymentsPage() {
  // Mock data for payments
  const currentBalance = 2847.5
  const weeklyEarnings = 485.2
  const monthlyEarnings = 1940.8
  const pendingWithdrawal = 0
  const nextPayoutDate = "March 15, 2024"

  const recentTransactions = [
    {
      id: 1,
      type: "earning",
      amount: 125.0,
      description: "Course: React Fundamentals",
      date: "2024-03-10",
      status: "completed",
    },
    {
      id: 2,
      type: "earning",
      amount: 89.5,
      description: "Course: JavaScript Basics",
      date: "2024-03-09",
      status: "completed",
    },
    {
      id: 3,
      type: "withdrawal",
      amount: -500.0,
      description: "Weekly Withdrawal",
      date: "2024-03-08",
      status: "completed",
    },
    {
      id: 4,
      type: "earning",
      amount: 156.75,
      description: "Course: HTML & CSS",
      date: "2024-03-07",
      status: "completed",
    },
    {
      id: 5,
      type: "earning",
      amount: 203.2,
      description: "Course: Modern JavaScript",
      date: "2024-03-06",
      status: "completed",
    },
  ]

  const weeklyPayments = [
    { week: "Week 1 (Mar 1-7)", amount: 645.3, status: "paid", date: "Mar 8" },
    { week: "Week 2 (Mar 8-14)", amount: 485.2, status: "pending", date: "Mar 15" },
    { week: "Week 3 (Mar 15-21)", amount: 0, status: "current", date: "Mar 22" },
    { week: "Week 4 (Mar 22-28)", amount: 0, status: "upcoming", date: "Mar 29" },
  ]

  return (
    <DashboardLayout>
      <div className="space-y-6">
        {/* Simple page title */}
        <div className="mb-6">
          <h1 className="text-3xl font-bold tracking-tight">Payments & Earnings</h1>
          <p className="text-muted-foreground">Manage your earnings and withdrawals</p>
        </div>

        {/* Payment Overview Cards */}
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">Current Balance</CardTitle>
              <IndianRupee className="h-4 w-4 text-muted-foreground" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold">₹{currentBalance.toFixed(2)}</div>
              <p className="text-xs text-muted-foreground">Available for withdrawal</p>
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">This Week</CardTitle>
              <Calendar className="h-4 w-4 text-muted-foreground" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold">₹{weeklyEarnings.toFixed(2)}</div>
              <p className="text-xs text-muted-foreground">+12% from last week</p>
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">This Month</CardTitle>
              <TrendingUp className="h-4 w-4 text-muted-foreground" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold">₹{monthlyEarnings.toFixed(2)}</div>
              <p className="text-xs text-muted-foreground">+8% from last month</p>
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">Next Payout</CardTitle>
              <Clock className="h-4 w-4 text-muted-foreground" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold">{nextPayoutDate}</div>
              <p className="text-xs text-muted-foreground">Weekly automatic payout</p>
            </CardContent>
          </Card>
        </div>

        {/* Main Content Tabs */}
        <Tabs defaultValue="overview" className="space-y-4">
          <TabsList>
            <TabsTrigger value="overview">Overview</TabsTrigger>
            <TabsTrigger value="weekly">Weekly Payments</TabsTrigger>
            <TabsTrigger value="withdraw">Withdraw</TabsTrigger>
            <TabsTrigger value="history">History</TabsTrigger>
          </TabsList>

          <TabsContent value="overview" className="space-y-4">
            <div className="grid gap-4 md:grid-cols-2">
              {/* Quick Actions */}
              <Card>
                <CardHeader>
                  <CardTitle>Quick Actions</CardTitle>
                  <CardDescription>Manage your payments and withdrawals</CardDescription>
                </CardHeader>
                <CardContent className="space-y-4">
                  <Button className="w-full" size="lg">
                    <Download className="mr-2 h-4 w-4" />
                    Request Withdrawal
                  </Button>
                  <Button variant="outline" className="w-full bg-transparent">
                    <CreditCard className="mr-2 h-4 w-4" />
                    Update Payment Method
                  </Button>
                  <Button variant="outline" className="w-full bg-transparent">
                    <Download className="mr-2 h-4 w-4" />
                    Download Tax Documents
                  </Button>
                </CardContent>
              </Card>

              {/* Payment Progress */}
              <Card>
                <CardHeader>
                  <CardTitle>Weekly Progress</CardTitle>
                  <CardDescription>Current week earnings progress</CardDescription>
                </CardHeader>
                <CardContent className="space-y-4">
                  <div className="space-y-2">
                    <div className="flex justify-between text-sm">
                      <span>Week Progress</span>
                      <span>5/7 days</span>
                    </div>
                    <Progress value={71} className="h-2" />
                  </div>
                  <div className="space-y-2">
                    <div className="flex justify-between text-sm">
                      <span>Earnings Goal</span>
                      <span>₹{weeklyEarnings.toFixed(2)} / ₹600</span>
                    </div>
                    <Progress value={81} className="h-2" />
                  </div>
                  <div className="text-sm text-muted-foreground">You're on track to exceed your weekly goal!</div>
                </CardContent>
              </Card>
            </div>

            {/* Recent Transactions */}
            <Card>
              <CardHeader>
                <CardTitle>Recent Transactions</CardTitle>
                <CardDescription>Your latest earnings and withdrawals</CardDescription>
              </CardHeader>
              <CardContent>
                <div className="space-y-4">
                  {recentTransactions.map((transaction) => (
                    <div key={transaction.id} className="flex items-center justify-between">
                      <div className="flex items-center space-x-4">
                        <div
                          className={`p-2 rounded-full ${
                            transaction.type === "earning"
                              ? "bg-green-100 text-green-600 dark:bg-green-900 dark:text-green-400"
                              : "bg-blue-100 text-blue-600 dark:bg-blue-900 dark:text-blue-400"
                          }`}
                        >
                          {transaction.type === "earning" ? (
                            <TrendingUp className="h-4 w-4" />
                          ) : (
                            <Download className="h-4 w-4" />
                          )}
                        </div>
                        <div>
                          <p className="text-sm font-medium">{transaction.description}</p>
                          <p className="text-xs text-muted-foreground">{transaction.date}</p>
                        </div>
                      </div>
                      <div className="text-right">
                        <p
                          className={`text-sm font-medium ${transaction.amount > 0 ? "text-green-600" : "text-blue-600"}`}
                        >
                          {transaction.amount > 0 ? "+" : ""}₹{Math.abs(transaction.amount).toFixed(2)}
                        </p>
                        <Badge variant={transaction.status === "completed" ? "default" : "secondary"}>
                          {transaction.status}
                        </Badge>
                      </div>
                    </div>
                  ))}
                </div>
              </CardContent>
            </Card>
          </TabsContent>

          <TabsContent value="weekly" className="space-y-4">
            <Card>
              <CardHeader>
                <CardTitle>Weekly Payment Schedule</CardTitle>
                <CardDescription>Track your weekly earnings and payment status</CardDescription>
              </CardHeader>
              <CardContent>
                <div className="space-y-4">
                  {weeklyPayments.map((payment, index) => (
                    <div key={index} className="flex items-center justify-between p-4 border rounded-lg">
                      <div className="flex items-center space-x-4">
                        <div
                          className={`p-2 rounded-full ${
                            payment.status === "paid"
                              ? "bg-green-100 text-green-600 dark:bg-green-900 dark:text-green-400"
                              : payment.status === "pending"
                                ? "bg-yellow-100 text-yellow-600 dark:bg-yellow-900 dark:text-yellow-400"
                                : payment.status === "current"
                                  ? "bg-blue-100 text-blue-600 dark:bg-blue-900 dark:text-blue-400"
                                  : "bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-400"
                          }`}
                        >
                          {payment.status === "paid" ? (
                            <CheckCircle className="h-4 w-4" />
                          ) : payment.status === "pending" ? (
                            <Clock className="h-4 w-4" />
                          ) : (
                            <AlertCircle className="h-4 w-4" />
                          )}
                        </div>
                        <div>
                          <p className="font-medium">{payment.week}</p>
                          <p className="text-sm text-muted-foreground">Payout: {payment.date}</p>
                        </div>
                      </div>
                      <div className="text-right">
                        <p className="font-medium">₹{payment.amount.toFixed(2)}</p>
                        <Badge
                          variant={
                            payment.status === "paid"
                              ? "default"
                              : payment.status === "pending"
                                ? "secondary"
                                : payment.status === "current"
                                  ? "outline"
                                  : "secondary"
                          }
                        >
                          {payment.status}
                        </Badge>
                      </div>
                    </div>
                  ))}
                </div>
              </CardContent>
            </Card>
          </TabsContent>

          <TabsContent value="withdraw" className="space-y-4">
            <div className="grid gap-4 md:grid-cols-2">
              <Card>
                <CardHeader>
                  <CardTitle>Withdrawal Request</CardTitle>
                  <CardDescription>Request a withdrawal from your available balance</CardDescription>
                </CardHeader>
                <CardContent className="space-y-4">
                  <div className="space-y-2">
                    <label className="text-sm font-medium">Available Balance</label>
                    <div className="text-2xl font-bold text-green-600">₹{currentBalance.toFixed(2)}</div>
                  </div>
                  <div className="space-y-2">
                    <label className="text-sm font-medium">Withdrawal Amount</label>
                    <input
                      type="number"
                      placeholder="Enter amount"
                      className="w-full p-2 border rounded-md"
                      max={currentBalance}
                    />
                  </div>
                  <Button className="w-full" size="lg">
                    Request Withdrawal
                  </Button>
                  <p className="text-xs text-muted-foreground">Withdrawals are processed within 1-3 business days</p>
                </CardContent>
              </Card>

              <Card>
                <CardHeader>
                  <CardTitle>Payment Method</CardTitle>
                  <CardDescription>Your current withdrawal method</CardDescription>
                </CardHeader>
                <CardContent className="space-y-4">
                  <div className="flex items-center space-x-4 p-4 border rounded-lg">
                    <CreditCard className="h-8 w-8 text-muted-foreground" />
                    <div>
                      <p className="font-medium">Bank Account</p>
                      <p className="text-sm text-muted-foreground">****1234</p>
                    </div>
                  </div>
                  <Button variant="outline" className="w-full bg-transparent">
                    Update Payment Method
                  </Button>
                </CardContent>
              </Card>
            </div>
          </TabsContent>

          <TabsContent value="history" className="space-y-4">
            <Card>
              <CardHeader>
                <CardTitle>Payment History</CardTitle>
                <CardDescription>Complete history of all your payments and withdrawals</CardDescription>
              </CardHeader>
              <CardContent>
                <div className="space-y-4">
                  {recentTransactions.map((transaction) => (
                    <div key={transaction.id} className="flex items-center justify-between p-4 border rounded-lg">
                      <div className="flex items-center space-x-4">
                        <div
                          className={`p-2 rounded-full ${
                            transaction.type === "earning"
                              ? "bg-green-100 text-green-600 dark:bg-green-900 dark:text-green-400"
                              : "bg-blue-100 text-blue-600 dark:bg-blue-900 dark:text-blue-400"
                          }`}
                        >
                          {transaction.type === "earning" ? (
                            <TrendingUp className="h-4 w-4" />
                          ) : (
                            <Download className="h-4 w-4" />
                          )}
                        </div>
                        <div>
                          <p className="font-medium">{transaction.description}</p>
                          <p className="text-sm text-muted-foreground">{transaction.date}</p>
                        </div>
                      </div>
                      <div className="text-right">
                        <p className={`font-medium ${transaction.amount > 0 ? "text-green-600" : "text-blue-600"}`}>
                          {transaction.amount > 0 ? "+" : ""}₹{Math.abs(transaction.amount).toFixed(2)}
                        </p>
                        <Badge variant={transaction.status === "completed" ? "default" : "secondary"}>
                          {transaction.status}
                        </Badge>
                      </div>
                    </div>
                  ))}
                </div>
              </CardContent>
            </Card>
          </TabsContent>
        </Tabs>
      </div>
    </DashboardLayout>
  )
}
