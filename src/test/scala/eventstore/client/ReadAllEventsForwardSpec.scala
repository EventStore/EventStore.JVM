package eventstore.client

import org.specs2.mutable.Specification

/**
 * @author Yaroslav Klymko
 */
class ReadAllEventsForwardSpec extends Specification {
  "read all events forward" should {
    "return empty slice if asked to read from end" in todo
    "return events in same order as written" in todo
    "be able to read all one by one until end of stream" in todo
    "be able to read events slice at time" in todo
    "return partial slice if not enough events" in todo
    
    /*[TestFixture, Category("LongRunning")]
    public class read all events forward should: SpecificationWithDirectoryPerTestFixture
    {
        private MiniNode  node;
        private IEventStoreConnection  conn;
        private EventData[]  testEvents;

        [TestFixtureSetUp]
        public override void TestFixtureSetUp()
        {
            base.TestFixtureSetUp();
             node = new MiniNode(PathName, skipInitializeStandardUsersCheck: false);
             node.Start();
             conn = TestConnection.Create( node.TcpEndPoint);
             conn.Connect();
             conn.SetStreamMetadata("$all", -1,
                                    StreamMetadata.Build().SetReadRole(SystemUserGroups.All),
                                    new UserCredentials(SystemUsers.Admin, SystemUsers.DefaultAdminPassword));

             testEvents = Enumerable.Range(0, 20).Select(x => TestEvent.NewTestEvent(x.ToString())).ToArray();
             conn.AppendToStream("stream", ExpectedVersion.EmptyStream,  testEvents);
        }

        [TestFixtureTearDown]
        public override void TestFixtureTearDown()
        {
             conn.Close();
             node.Shutdown();
            base.TestFixtureTearDown();
        }

        [Test, Category("LongRunning")]
        public void return empty slice if asked to read from end()
        {
            var read =  conn.ReadAllEventsForward(Position.End, 1, false);
            Assert.That(read.IsEndOfStream, Is.True);
            Assert.That(read.Events.Length, Is.EqualTo(0));
        }

        [Test, Category("LongRunning")]
        public void return events in same order as written()
        {
            var read =  conn.ReadAllEventsForward(Position.Start,  testEvents.Length + 10, false);
            Assert.That(EventDataComparer.Equal(
                 testEvents.ToArray(),
                read.Events.Skip(read.Events.Length -  testEvents.Length).Select(x => x.Event).ToArray()));
        }

        [Test, Category("LongRunning")]
        public void be able to read all one by one until end of stream()
        {
            var all = new List<RecordedEvent>();
            var position = Position.Start;
            AllEventsSlice slice;

            while (!(slice =  conn.ReadAllEventsForward(position, 1, false)).IsEndOfStream)
            {
                all.Add(slice.Events.Single().Event);
                position = slice.NextPosition;
            }

            Assert.That(EventDataComparer.Equal( testEvents, all.Skip(all.Count -  testEvents.Length).ToArray()));
        }

        [Test, Category("LongRunning")]
        public void be able to read events slice at time()
        {
            var all = new List<RecordedEvent>();
            var position = Position.Start;
            AllEventsSlice slice;

            while (!(slice =  conn.ReadAllEventsForward(position, 5, false)).IsEndOfStream)
            {
                all.AddRange(slice.Events.Select(x => x.Event));
                position = slice.NextPosition;
            }

            Assert.That(EventDataComparer.Equal( testEvents, all.Skip(all.Count -  testEvents.Length).ToArray()));
        }

        [Test, Category("LongRunning")]
        public void return partial slice if not enough events()
        {
            var read =  conn.ReadAllEventsForward(Position.Start, 30, false);
            Assert.That(read.Events.Length, Is.LessThan(30));
            Assert.That(EventDataComparer.Equal(
                 testEvents,
                read.Events.Skip(read.Events.Length -  testEvents.Length).Select(x => x.Event).ToArray()));
        }
    }*/
  }
}
