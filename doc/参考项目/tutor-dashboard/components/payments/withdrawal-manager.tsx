"use client"

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Textarea } from "@/components/ui/textarea"
import { Switch } from "@/components/ui/switch"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog"
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog"
import {
  CreditCard,
  Download,
  Clock,
  CheckCircle,
  XCircle,
  AlertCircle,
  Plus,
  Edit,
  Trash2,
  Shield,
  Zap,
  Calendar,
} from "lucide-react"
import { useState } from "react"

export default function WithdrawalManager() {
  const [withdrawalAmount, setWithdrawalAmount] = useState("")
  const [selectedMethod, setSelectedMethod] = useState("")
  const [withdrawalType, setWithdrawalType] = useState("standard")
  const [autoWithdrawal, setAutoWithdrawal] = useState(false)
  const [isRequestDialogOpen, setIsRequestDialogOpen] = useState(false)

  const availableBalance = 2847.5
  const minimumWithdrawal = 50
  const maximumWithdrawal = 5000

  // Mock data for payment methods
  const paymentMethods = [
    {
      id: "1",
      type: "bank",
      name: "Chase Bank",
      details: "****1234",
      isDefault: true,
      processingTime: "1-3 business days",
      fee: 0,
    },
    {
      id: "2",
      type: "paypal",
      name: "PayPal",
      details: "john@example.com",
      isDefault: false,
      processingTime: "Instant",
      fee: 2.5,
    },
    {
      id: "3",
      type: "crypto",
      name: "Bitcoin Wallet",
      details: "1A1z...Nx7B",
      isDefault: false,
      processingTime: "10-30 minutes",
      fee: 1.0,
    },
  ]

  // Mock data for withdrawal history
  const withdrawalHistory = [
    {
      id: "1",
      amount: 500.0,
      method: "Chase Bank",
      type: "standard",
      status: "completed",
      requestDate: "2024-03-08",
      completedDate: "2024-03-10",
      fee: 0,
      reference: "WD001234",
    },
    {
      id: "2",
      amount: 750.0,
      method: "PayPal",
      type: "instant",
      status: "completed",
      requestDate: "2024-03-01",
      completedDate: "2024-03-01",
      fee: 18.75,
      reference: "WD001235",
    },
    {
      id: "3",
      amount: 300.0,
      method: "Chase Bank",
      type: "standard",
      status: "pending",
      requestDate: "2024-03-12",
      completedDate: null,
      fee: 0,
      reference: "WD001236",
    },
    {
      id: "4",
      amount: 200.0,
      method: "Bitcoin Wallet",
      type: "crypto",
      status: "processing",
      requestDate: "2024-03-11",
      completedDate: null,
      fee: 2.0,
      reference: "WD001237",
    },
  ]

  const getStatusIcon = (status: string) => {
    switch (status) {
      case "completed":
        return <CheckCircle className="h-4 w-4 text-green-600" />
      case "pending":
        return <Clock className="h-4 w-4 text-yellow-600" />
      case "processing":
        return <AlertCircle className="h-4 w-4 text-blue-600" />
      case "failed":
        return <XCircle className="h-4 w-4 text-red-600" />
      default:
        return <Clock className="h-4 w-4 text-gray-400" />
    }
  }

  const getStatusColor = (status: string) => {
    switch (status) {
      case "completed":
        return "bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200"
      case "pending":
        return "bg-yellow-100 text-yellow-800 dark:bg-yellow-900 dark:text-yellow-200"
      case "processing":
        return "bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-200"
      case "failed":
        return "bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200"
      default:
        return "bg-gray-100 text-gray-800 dark:bg-gray-800 dark:text-gray-200"
    }
  }

  const getMethodIcon = (type: string) => {
    switch (type) {
      case "bank":
        return <CreditCard className="h-5 w-5" />
      case "paypal":
        return <div className="h-5 w-5 bg-blue-600 rounded text-white text-xs flex items-center justify-center">P</div>
      case "crypto":
        return (
          <div className="h-5 w-5 bg-orange-500 rounded text-white text-xs flex items-center justify-center">₿</div>
        )
      default:
        return <CreditCard className="h-5 w-5" />
    }
  }

  const calculateFee = (amount: number, type: string) => {
    const method = paymentMethods.find((m) => m.id === selectedMethod)
    if (!method) return 0
    return type === "instant" ? amount * 0.025 : method.fee
  }

  const handleWithdrawalRequest = () => {
    // Handle withdrawal request logic here
    console.log("Withdrawal requested:", {
      amount: withdrawalAmount,
      method: selectedMethod,
      type: withdrawalType,
    })
    setIsRequestDialogOpen(false)
    setWithdrawalAmount("")
  }

  return (
    <div className="space-y-6">
      {/* Withdrawal Overview */}
      <div className="grid gap-4 md:grid-cols-3">
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Available Balance</CardTitle>
            <Download className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-green-600">₹{availableBalance.toFixed(2)}</div>
            <p className="text-xs text-muted-foreground">Ready for withdrawal</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Pending Withdrawals</CardTitle>
            <Clock className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-yellow-600">
              ₹
              {withdrawalHistory
                .filter((w) => w.status === "pending" || w.status === "processing")
                .reduce((sum, w) => sum + w.amount, 0)
                .toFixed(2)}
            </div>
            <p className="text-xs text-muted-foreground">Being processed</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">This Month</CardTitle>
            <CheckCircle className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">
              ₹
              {withdrawalHistory
                .filter((w) => w.status === "completed" && new Date(w.requestDate).getMonth() === new Date().getMonth())
                .reduce((sum, w) => sum + w.amount, 0)
                .toFixed(2)}
            </div>
            <p className="text-xs text-muted-foreground">Total withdrawn</p>
          </CardContent>
        </Card>
      </div>

      <Tabs defaultValue="request" className="space-y-4">
        <TabsList>
          <TabsTrigger value="request">New Withdrawal</TabsTrigger>
          <TabsTrigger value="history">History</TabsTrigger>
          <TabsTrigger value="methods">Payment Methods</TabsTrigger>
          <TabsTrigger value="settings">Settings</TabsTrigger>
        </TabsList>

        <TabsContent value="request" className="space-y-4">
          <div className="grid gap-6 md:grid-cols-2">
            <Card>
              <CardHeader>
                <CardTitle>Request Withdrawal</CardTitle>
                <CardDescription>Withdraw your earnings to your preferred payment method</CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="space-y-2">
                  <Label htmlFor="amount">Withdrawal Amount</Label>
                  <Input
                    id="amount"
                    type="number"
                    placeholder="Enter amount"
                    value={withdrawalAmount}
                    onChange={(e) => setWithdrawalAmount(e.target.value)}
                    min={minimumWithdrawal}
                    max={Math.min(availableBalance, maximumWithdrawal)}
                  />
                  <p className="text-xs text-muted-foreground">
                    Min: ₹{minimumWithdrawal} • Max: ₹{Math.min(availableBalance, maximumWithdrawal).toFixed(2)}
                  </p>
                </div>

                <div className="space-y-2">
                  <Label htmlFor="method">Payment Method</Label>
                  <Select value={selectedMethod} onValueChange={setSelectedMethod}>
                    <SelectTrigger>
                      <SelectValue placeholder="Select payment method" />
                    </SelectTrigger>
                    <SelectContent>
                      {paymentMethods.map((method) => (
                        <SelectItem key={method.id} value={method.id}>
                          <div className="flex items-center space-x-2">
                            {getMethodIcon(method.type)}
                            <span>{method.name}</span>
                            <span className="text-muted-foreground">({method.details})</span>
                            {method.isDefault && <Badge variant="secondary">Default</Badge>}
                          </div>
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>

                <div className="space-y-2">
                  <Label htmlFor="type">Withdrawal Type</Label>
                  <Select value={withdrawalType} onValueChange={setWithdrawalType}>
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="standard">
                        <div className="flex items-center space-x-2">
                          <Calendar className="h-4 w-4" />
                          <div>
                            <p>Standard (Free)</p>
                            <p className="text-xs text-muted-foreground">1-3 business days</p>
                          </div>
                        </div>
                      </SelectItem>
                      <SelectItem value="instant">
                        <div className="flex items-center space-x-2">
                          <Zap className="h-4 w-4" />
                          <div>
                            <p>Instant (2.5% fee)</p>
                            <p className="text-xs text-muted-foreground">Within minutes</p>
                          </div>
                        </div>
                      </SelectItem>
                    </SelectContent>
                  </Select>
                </div>

                {withdrawalAmount && selectedMethod && (
                  <div className="p-4 bg-muted rounded-lg space-y-2">
                    <div className="flex justify-between">
                      <span>Withdrawal Amount:</span>
                      <span>₹{Number.parseFloat(withdrawalAmount).toFixed(2)}</span>
                    </div>
                    <div className="flex justify-between">
                      <span>Processing Fee:</span>
                      <span>₹{calculateFee(Number.parseFloat(withdrawalAmount), withdrawalType).toFixed(2)}</span>
                    </div>
                    <div className="flex justify-between font-medium border-t pt-2">
                      <span>You'll Receive:</span>
                      <span>
                        ₹
                        {(
                          Number.parseFloat(withdrawalAmount) -
                          calculateFee(Number.parseFloat(withdrawalAmount), withdrawalType)
                        ).toFixed(2)}
                      </span>
                    </div>
                  </div>
                )}

                <Dialog open={isRequestDialogOpen} onOpenChange={setIsRequestDialogOpen}>
                  <DialogTrigger asChild>
                    <Button
                      className="w-full"
                      size="lg"
                      disabled={
                        !withdrawalAmount || !selectedMethod || Number.parseFloat(withdrawalAmount) < minimumWithdrawal
                      }
                    >
                      <Download className="mr-2 h-4 w-4" />
                      Request Withdrawal
                    </Button>
                  </DialogTrigger>
                  <DialogContent>
                    <DialogHeader>
                      <DialogTitle>Confirm Withdrawal</DialogTitle>
                      <DialogDescription>Please review your withdrawal details before confirming.</DialogDescription>
                    </DialogHeader>
                    <div className="space-y-4">
                      <div className="p-4 border rounded-lg space-y-2">
                        <div className="flex justify-between">
                          <span>Amount:</span>
                          <span className="font-medium">₹{Number.parseFloat(withdrawalAmount || "0").toFixed(2)}</span>
                        </div>
                        <div className="flex justify-between">
                          <span>Method:</span>
                          <span className="font-medium">
                            {paymentMethods.find((m) => m.id === selectedMethod)?.name}
                          </span>
                        </div>
                        <div className="flex justify-between">
                          <span>Type:</span>
                          <span className="font-medium capitalize">{withdrawalType}</span>
                        </div>
                        <div className="flex justify-between">
                          <span>Fee:</span>
                          <span className="font-medium">
                            ₹{calculateFee(Number.parseFloat(withdrawalAmount || "0"), withdrawalType).toFixed(2)}
                          </span>
                        </div>
                        <div className="flex justify-between border-t pt-2 font-medium">
                          <span>You'll Receive:</span>
                          <span>
                            ₹
                            {(
                              Number.parseFloat(withdrawalAmount || "0") -
                              calculateFee(Number.parseFloat(withdrawalAmount || "0"), withdrawalType)
                            ).toFixed(2)}
                          </span>
                        </div>
                      </div>
                    </div>
                    <DialogFooter>
                      <Button variant="outline" onClick={() => setIsRequestDialogOpen(false)}>
                        Cancel
                      </Button>
                      <Button onClick={handleWithdrawalRequest}>Confirm Withdrawal</Button>
                    </DialogFooter>
                  </DialogContent>
                </Dialog>
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>Quick Actions</CardTitle>
                <CardDescription>Common withdrawal amounts</CardDescription>
              </CardHeader>
              <CardContent className="space-y-3">
                {[100, 250, 500, 1000].map((amount) => (
                  <Button
                    key={amount}
                    variant="outline"
                    className="w-full justify-between bg-transparent"
                    onClick={() => setWithdrawalAmount(amount.toString())}
                    disabled={amount > availableBalance}
                  >
                    <span>Withdraw ₹{amount}</span>
                    <span className="text-muted-foreground">
                      {amount <= availableBalance ? "Available" : "Insufficient"}
                    </span>
                  </Button>
                ))}
                <Button
                  variant="outline"
                  className="w-full justify-between bg-transparent"
                  onClick={() => setWithdrawalAmount(availableBalance.toString())}
                >
                  <span>Withdraw All</span>
                  <span className="text-muted-foreground">₹{availableBalance.toFixed(2)}</span>
                </Button>
              </CardContent>
            </Card>
          </div>
        </TabsContent>

        <TabsContent value="history" className="space-y-4">
          <Card>
            <CardHeader>
              <CardTitle>Withdrawal History</CardTitle>
              <CardDescription>Track all your withdrawal requests and their status</CardDescription>
            </CardHeader>
            <CardContent>
              <div className="space-y-4">
                {withdrawalHistory.map((withdrawal) => (
                  <div key={withdrawal.id} className="flex items-center justify-between p-4 border rounded-lg">
                    <div className="flex items-center space-x-4">
                      {getStatusIcon(withdrawal.status)}
                      <div>
                        <p className="font-medium">₹{withdrawal.amount.toFixed(2)}</p>
                        <p className="text-sm text-muted-foreground">
                          {withdrawal.method} • {withdrawal.type}
                        </p>
                        <p className="text-xs text-muted-foreground">Ref: {withdrawal.reference}</p>
                      </div>
                    </div>
                    <div className="text-right">
                      <Badge className={getStatusColor(withdrawal.status)}>{withdrawal.status}</Badge>
                      <p className="text-xs text-muted-foreground mt-1">
                        {withdrawal.completedDate || withdrawal.requestDate}
                      </p>
                      {withdrawal.fee > 0 && (
                        <p className="text-xs text-muted-foreground">Fee: ₹{withdrawal.fee.toFixed(2)}</p>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="methods" className="space-y-4">
          <Card>
            <CardHeader>
              <div className="flex items-center justify-between">
                <div>
                  <CardTitle>Payment Methods</CardTitle>
                  <CardDescription>Manage your withdrawal destinations</CardDescription>
                </div>
                <Button>
                  <Plus className="mr-2 h-4 w-4" />
                  Add Method
                </Button>
              </div>
            </CardHeader>
            <CardContent>
              <div className="space-y-4">
                {paymentMethods.map((method) => (
                  <div key={method.id} className="flex items-center justify-between p-4 border rounded-lg">
                    <div className="flex items-center space-x-4">
                      {getMethodIcon(method.type)}
                      <div>
                        <div className="flex items-center space-x-2">
                          <p className="font-medium">{method.name}</p>
                          {method.isDefault && <Badge variant="secondary">Default</Badge>}
                        </div>
                        <p className="text-sm text-muted-foreground">{method.details}</p>
                        <p className="text-xs text-muted-foreground">
                          {method.processingTime} • {method.fee > 0 ? `${method.fee}% fee` : "No fee"}
                        </p>
                      </div>
                    </div>
                    <div className="flex items-center space-x-2">
                      <Button variant="outline" size="sm" className="bg-transparent">
                        <Edit className="h-4 w-4" />
                      </Button>
                      <AlertDialog>
                        <AlertDialogTrigger asChild>
                          <Button variant="outline" size="sm" className="bg-transparent">
                            <Trash2 className="h-4 w-4" />
                          </Button>
                        </AlertDialogTrigger>
                        <AlertDialogContent>
                          <AlertDialogHeader>
                            <AlertDialogTitle>Delete Payment Method</AlertDialogTitle>
                            <AlertDialogDescription>
                              Are you sure you want to delete this payment method? This action cannot be undone.
                            </AlertDialogDescription>
                          </AlertDialogHeader>
                          <AlertDialogFooter>
                            <AlertDialogCancel>Cancel</AlertDialogCancel>
                            <AlertDialogAction>Delete</AlertDialogAction>
                          </AlertDialogFooter>
                        </AlertDialogContent>
                      </AlertDialog>
                    </div>
                  </div>
                ))}
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="settings" className="space-y-4">
          <Card>
            <CardHeader>
              <CardTitle>Withdrawal Settings</CardTitle>
              <CardDescription>Configure your withdrawal preferences</CardDescription>
            </CardHeader>
            <CardContent className="space-y-6">
              <div className="flex items-center justify-between">
                <div className="space-y-0.5">
                  <Label>Auto Withdrawal</Label>
                  <p className="text-sm text-muted-foreground">
                    Automatically withdraw earnings weekly when balance exceeds ₹500
                  </p>
                </div>
                <Switch checked={autoWithdrawal} onCheckedChange={setAutoWithdrawal} />
              </div>

              <div className="space-y-2">
                <Label htmlFor="min-balance">Minimum Balance Threshold</Label>
                <Input id="min-balance" type="number" placeholder="500" />
                <p className="text-xs text-muted-foreground">
                  Auto withdrawal will trigger when your balance exceeds this amount
                </p>
              </div>

              <div className="space-y-2">
                <Label htmlFor="notification">Withdrawal Notifications</Label>
                <Textarea
                  id="notification"
                  placeholder="Enter email addresses for withdrawal notifications..."
                  className="min-h-[100px]"
                />
              </div>

              <div className="flex items-center space-x-2 p-4 bg-muted rounded-lg">
                <Shield className="h-5 w-5 text-blue-600" />
                <div>
                  <p className="font-medium">Security Notice</p>
                  <p className="text-sm text-muted-foreground">
                    All withdrawals are subject to security verification and may take additional time for processing.
                  </p>
                </div>
              </div>

              <Button className="w-full">Save Settings</Button>
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  )
}
