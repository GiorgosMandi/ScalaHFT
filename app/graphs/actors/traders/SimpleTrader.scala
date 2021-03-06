package graphs.actors.traders

import akka.actor.{Actor, ActorLogging}
import graphs.actors.traders.SimpleTrader.{ACK, Fail, Init, Terminate}
import utils.constants.TradeAction.{BUY, PRINT, SELL}

class SimpleTrader extends Actor with ActorLogging {

    val MIN_DEPOSIT = 5f
    val MAX_CAPITAL = 300f
    val INITIAL_CAPITAL = 100f
    val PROFIT_BASE = 10f


    override def receive: Receive = {
        case Init =>
            sender() ! ACK
            trade(INITIAL_CAPITAL, 0f, Nil)
        case Fail(ex) =>
            log.warning(s"Stream Failed: $ex")
    }

    def trade(capital: Double, deposits: Double, orders: List[(Double, Double)]): Receive = {
        /**
         * In case of a BUY, extract 20% of the current capital
         *  and place an order. An order is defined as the
         *  size of the investment and the given price
         */
        case (BUY, price: Double) =>
            val investment = capital * .2
            val capitalAfterOrder = capital - investment
            val newOrder = (price, investment)

            log.warning(s"BUYING: $newOrder")
            context.become(trade(capitalAfterOrder, deposits, newOrder :: orders))
            sender() ! ACK

        case (SELL, price: Double) =>
            /**
             * When we SELL, first we find the orders which are profitable if we sell them
             * with the given price. Then we compute the profit, based on the differences
             * of the BUYing price and the SELLing price.
             */
            val (profitablePurchases, rest) = orders.partition(_._1 < price)
            val previousInvestments = profitablePurchases.map(_._2).sum
            val profit = profitablePurchases
              .map { case (previousPrice, investment) => (price * investment) - (previousPrice * investment) }
              .sum
            log.warning(s"SELLING: PROFIT: $profit")

            val depositedProfit = depositProfit(profit, PROFIT_BASE, MIN_DEPOSIT)
            val capitalizedProfit = profit - depositedProfit
            val newCapital = previousInvestments + capitalizedProfit + capital
            val depositedCapital = depositCapital(newCapital, MAX_CAPITAL, MIN_DEPOSIT)
            val newDeposits = deposits + depositedProfit + depositedCapital
            context.become(trade(newCapital-depositedCapital, newDeposits, rest))
            sender() ! ACK

        case PRINT =>
            print(capital, deposits, orders)
            sender() ! ACK

        case Fail(ex) =>
            log.warning(s"Stream Failed: $ex")
        case Terminate =>
            print(capital, deposits, orders)
            sender() ! ACK
            context.unbecome()
    }

    def print(capital: Double, deposits: Double, orders: List[(Double, Double)]): Unit =
        log.warning(s"\n---\nCapital: $capital\nDeposits: $deposits\nExisting Purchases: $orders\n----")

    def depositProfit(profit: Double, base: Double, minDeposit: Double): Double = {
        val ratio = profit/ (profit+base)
        val deposit =  ratio*profit
        if (deposit > minDeposit) deposit else 0f
    }

    def depositCapital(capital: Double, maxCapital: Double, minDeposit: Double): Double ={
        if (capital > maxCapital){
            val capitalBase = maxCapital * .5
            depositProfit(capital, capitalBase, minDeposit)
        }
        else
            0f
    }
}

object SimpleTrader{
    case object Init
    case object ACK
    case object Terminate
    case class Fail(ex: Throwable)
}
