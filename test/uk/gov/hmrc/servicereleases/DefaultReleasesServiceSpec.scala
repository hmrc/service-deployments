package uk.gov.hmrc.servicereleases

import java.time.LocalDateTime

import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatest.{BeforeAndAfterEach, Matchers, WordSpec}
import uk.gov.hmrc.servicereleases.deployments.{ServiceDeployment, ServiceDeploymentsService}
import uk.gov.hmrc.servicereleases.services.{Repository, ServiceRepositoriesService}
import uk.gov.hmrc.servicereleases.tags.{Tag, TagsService}

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class DefaultReleasesServiceSpec extends WordSpec with Matchers with MockitoSugar with ScalaFutures with BeforeAndAfterEach {
  val servicesService = mock[ServiceRepositoriesService]
  val deploymentsService = mock[ServiceDeploymentsService]
  val tagsService = mock[TagsService]
  val repository = mock[ReleasesRepository]

  override def beforeEach() = {
    reset(servicesService)
    reset(deploymentsService)
    reset(tagsService)
    reset(repository)
  }

  "updateModel" should {
    val service = new DefaultReleasesService(servicesService, deploymentsService, tagsService, repository)

    "Add any new releases for known services to the mongo repository" in {
      val testData = new TestData()
        .withKnownVersions("1.0.0", "2.0.0").withNewVersions("3.0.0").forService("service")
        .withKnownVersions("1.0.0").withNewVersions("1.1.0").forService("another")
        .setup()

      val result = service.updateModel().futureValue

      testData.verifyReleasesAddedToRepository()
    }

    "Add new releases when a service has never been seen before" in {
      val testData = new TestData()
        .withKnownVersions().withNewVersions("1.0.0").forService("service")
        .setup()

      val result = service.updateModel().futureValue

      testData.verifyReleasesAddedToRepository()
    }

    "Cope with a scenario where there are no deployments at all for a service" in {
      val testData = new TestData()
        .withKnownVersions().withNewVersions().forService("service")
        .withKnownVersions().withNewVersions().forService("another")
        .setup()

      val result = service.updateModel().futureValue

      testData.verifyReleasesAddedToRepository()
    }

    "Ignore new releases for services if we fail to fetch the tags" in {
      val testData = new TestData()
        .withKnownVersions("1.0.0", "2.0.0").withNewVersions("3.0.0").forService("service")
        .withKnownVersions("1.0.0").withNewVersions("1.1.0").forService("another", withTagFailure = true)
        .setup()

      val result = service.updateModel().futureValue

      testData.verifyReleasesAddedToRepository()
    }

    "Not do anything if there are no new releases" in {
      new TestData()
        .withKnownVersions("1.0.0", "2.0.0").forService("service")
        .withKnownVersions("1.0.0").forService("another")
        .setup()

      val result = service.updateModel().futureValue

      verify(tagsService, never).get(any(), any(), any())
      verify(repository, never).add(any())
    }
  }

  private class TestData() {

    private case class ServiceTestData(serviceName: String,
                               expected: Seq[(String, LocalDateTime, LocalDateTime)],
                               knownVersions: Seq[String],
                               unknownVersions: Seq[String])

    private var serviceData: Seq[ServiceTestData] = Seq()

    val now = LocalDateTime.of(2016, 1, 1, 1, 1)
    var expected: Seq[(String, LocalDateTime, LocalDateTime)] = Seq()
    var knownVersions: Seq[String] = List()
    var unknownVersions: Seq[String] = List()

    def withKnownVersions(versions: String*) = {
      knownVersions = versions
      this
    }

    def withNewVersions(versions: String*) = {
      unknownVersions = versions
      this
    }

    def forService(name: String = RandomData.string(8), withTagFailure: Boolean = false) = {

      if (withTagFailure)
        when(tagsService.get("hmrc", name, "github")).thenReturn(
          Future.successful(Failure(new RuntimeException("Ooooarrrg"))))
      else {
        expected = unknownVersions.map { v =>
          val date = RandomData.date()
          (v, date, date.plusHours(1))
        }

        when(tagsService.get("hmrc", name, "github"))
          .thenReturn(Future.successful(
            Success((knownVersions.map { v => Tag(v, now) } ++
              expected.map { case (v, t, p) => Tag(v, t) }).toList)))
      }

      serviceData = serviceData :+ ServiceTestData(name, expected, knownVersions, unknownVersions)

      expected = Seq()
      knownVersions = List()
      unknownVersions = List()

      this
    }


//    def forService(name: String = RandomData.string(8), withTagFailure: Boolean = false) = {
//      if (withTagFailure)
//        when(tagsService.get("hmrc", name, "github")).thenReturn(
//          Future.successful(Failure(new RuntimeException("Ooorraaaggg"))))
//      else {
//        when(tagsService.get("hmrc", name, "github"))
//          .thenReturn(Future.successful(
//            Success((knownVersions.map { v => Tag(v, now) } ++
//              expected.map { case (v, t, p) => Tag(v, t) }).toList )))
//
//        expected = unknownVersions.map { v =>
//          val date = RandomData.date()
//          (v, date, date.plusHours(1))
//        }
//      }
//
//      serviceData = serviceData :+ ServiceTestData(name, expected, knownVersions, unknownVersions)
//
//      expected = Seq()
//      knownVersions = List()
//      unknownVersions = List()
//
//      this
//    }

    def setup() = {
      when(servicesService.getAll()).thenReturn(Future.successful(
        serviceData.map { sd => sd.serviceName -> List(Repository("hmrc", "github")) } toMap ))

      when(deploymentsService.getAll()).thenReturn(Future.successful(
        serviceData
          .map { sd => sd.serviceName -> buildDeploymentsData(sd) }
          .filter { case (_, sd) => sd.nonEmpty }
          .toMap
      ))

      when(repository.getAll()).thenReturn(Future.successful(serviceData
        .map { sd => sd.serviceName -> sd.knownVersions.map { v => Release(sd.serviceName, v, now, now) } }
        .filter { case (_, r) => r.nonEmpty }
        .toMap
      ))

      when(repository.add(any())).thenReturn(Future.successful(true))

      this
    }

    def withNoDeployments() =
      when(deploymentsService.getAll()).thenReturn(Future.successful(Map[String, Seq[ServiceDeployment]]()))

    private def buildDeploymentsData(sd: ServiceTestData): Seq[ServiceDeployment] = {
      sd.knownVersions.map { v => ServiceDeployment(v, now) } ++
        sd.expected.map { case (v, t, p) => ServiceDeployment(v, p) }
    }

    def verifyReleasesAddedToRepository() =
      serviceData.foreach { case sd =>
        sd.expected.foreach { case (v, t, p) => verify(repository).add(Release(sd.serviceName, v, t, p)) } }
  }

}

