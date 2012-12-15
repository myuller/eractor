Eractor
=======

Non-exhaustive Erlang style actors for scala Akka framework on top of delimited continuations.

## Example

    class PingPong extends Actor with Eractor {
        var pings = 0
        var pongs = 0

        def body = {

            react {
                case 'ping =>
                    pings += 1
                    self ! 'pong
            }

            react {
                case 'pong =>
                    pongs += 1
            }

            assert(pings == pongs)

            body // loop forever
        }
    }

    val pingPong = system.actorOf(Props(new PingPong))
    (1 to 1000).foreach(_ => pingPong ! 'ping)

## Caveats

Unfortunately, Eractor API didn't come out as clean
as in Erlang so here are some things to watch out for
when using library:

* "body" and "react" functions of Eractor are implemented
  using scala delimited continuations compiler plugin (and
  basically stand for "reset" and "shift" respectively).
  There is a good explanation of continuations and their
  usage limitations at http://jim-mcbeath.blogspot.ru/2010/08/delimited-continuations.html

* Matches in "react" are non-exhaustive which means that
  if incoming message doesn't match anything it will
  be put in queue to be tried later. This fact has important
  consequence: you cannot trust "sender" property of actor,
  which holds reference to last message _received_.
  To provide workaround method "realSender" was introduced,
  which contains captured sender when "react" method's
  partial function is being executed.

* You cannot do nested reacts in current implementation
  of library, so following will not compile:

    ...
    body = {
        react {
            case 'something =>
                ...
                react {
                    case 'otherThing =>
                        ...
                }
        }
    }